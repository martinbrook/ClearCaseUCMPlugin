package net.praqma.hudson.test;

import hudson.Launcher;
import hudson.model.*;
import hudson.model.Project;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import net.praqma.clearcase.ucm.entities.*;
import net.praqma.clearcase.ucm.view.UCMView;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import net.praqma.clearcase.PVob;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.CleartoolException;
import net.praqma.clearcase.ucm.entities.Project.PromotionLevel;
import net.praqma.clearcase.util.ExceptionUtils;
import net.praqma.hudson.CCUCMBuildAction;
import net.praqma.hudson.scm.CCUCMScm;
import net.praqma.hudson.scm.ChangeLogEntryImpl;

import static org.junit.Assert.*;

public class CCUCMRule extends JenkinsRule {
	
	private static Logger logger = Logger.getLogger( CCUCMRule.class.getName() );

	private CCUCMScm scm;

    private File outputDir;

    public CCUCMRule setOutputDir( String name ) {
        if( System.getenv().containsKey( "BUILD_NUMBER" ) ) {
            String bname = System.getenv( "JOB_NAME" );
            Integer number = new Integer( System.getenv( "BUILD_NUMBER" ) );

            this.outputDir = new File( new File( new File( new File( System.getProperty( "user.dir" ) ), "test-logs" ), number.toString() ), getSafeName( name ) );
        } else {
            this.outputDir = new File( new File( System.getProperty( "user.dir" ) ), "runs" );
        }

        logger.fine( "Logging to " + outputDir.getAbsolutePath() );

        this.outputDir.mkdirs();

        return this;
    }

    public static String getSafeName( String name ) {
        return name.replaceAll( "[^\\w]", "_" );
    }

    public static class ProjectCreator {

        public enum Type {
            self,
            child
        }
        String name;
        Type type = Type.self;
        String component;
        String stream;
        boolean recommend = false;
        boolean tag = false;
        boolean description = false;
        boolean createBaseline = false;
        boolean forceDeliver = false;
        boolean swipe = true;
        boolean trim = false;

        String template = "[project]_build_[number]";
        PromotionLevel promotionLevel = PromotionLevel.INITIAL;

        Class<? extends TopLevelItem> projectClass = FreeStyleProject.class;

        public ProjectCreator( String name, String component, String stream ) {
            this.name = name;
            this.component = component;
            this.stream = stream;
        }

        public String getName() {
            return name;
        }

        public ProjectCreator setType( Type type ) {
            this.type = type;
            return this;
        }

        public ProjectCreator setTagged( boolean tagged ) {
            this.tag = tagged;
            return this;
        }

        public ProjectCreator setDescribed( boolean described ) {
            this.description = described;
            return this;
        }

        public ProjectCreator setRecommend( boolean recommend ) {
            this.recommend = recommend;
            return this;
        }

        public ProjectCreator setCreateBaseline( boolean createBaseline ) {
            this.createBaseline = createBaseline;
            return this;
        }

        public ProjectCreator setPromotionLevel( PromotionLevel level ) {
            this.promotionLevel = level;

            return this;
        }

        public ProjectCreator setForceDeliver( boolean forceDeliver ) {
            this.forceDeliver = forceDeliver;

            return this;
        }

        public ProjectCreator setSwipe( boolean swipe ) {
            this.swipe = swipe;

            return this;
        }

        public ProjectCreator setTrim( boolean trim ) {
            this.trim = trim;
            return this;
        }

        public Project getProject() throws IOException {
            System.out.println( "==== [Setting up ClearCase UCM project] ====" );
            System.out.println( " * Stream         : " + stream );
            System.out.println( " * Component      : " + component );
            System.out.println( " * Level          : " + promotionLevel );
            System.out.println( " * Polling        : " + type );
            System.out.println( " * Recommend      : " + recommend );
            System.out.println( " * Tag            : " + tag );
            System.out.println( " * Description    : " + description );
            System.out.println( " * Create baseline: " + createBaseline );
            System.out.println( " * Template       : " + template );
            System.out.println( " * Force deliver  : " + forceDeliver );
            System.out.println( " * Swipe          : " + swipe );
            System.out.println( " * Trim           : " + trim );
            System.out.println( "============================================" );

            Project project = (Project) Hudson.getInstance().createProject( projectClass, name );

            // boolean createBaseline, String nameTemplate, boolean forceDeliver, boolean recommend, boolean makeTag, boolean setDescription
            //CCUCMScm scm = new CCUCMScm( component, "INITIAL", "ALL", false, type, stream, "successful", createBaseline, "[project]_build_[number]", forceDeliver, recommend, tag, description, "jenkins" );
            CCUCMScm scm = new CCUCMScm( component, ( promotionLevel != null ? promotionLevel.name() : "ANY" ), "ALL", false, type.name(), stream, "successful", createBaseline, template, forceDeliver, recommend, tag, description, "", swipe, trim );
            project.setScm( scm );

            return project;
        }
    }
	
	public FreeStyleProject setupProject( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean createBaseline ) throws Exception {
		return setupProject( projectName, type, component, stream, recommend, tag, description, createBaseline, false );
	}
	
	public FreeStyleProject setupProject( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean createBaseline, boolean forceDeliver ) throws Exception {
		return setupProject( projectName, type, component, stream, recommend, tag, description, createBaseline, forceDeliver, "[project]_build_[number]" );
	}
	
	public FreeStyleProject setupProject( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean createBaseline, boolean forceDeliver, String template ) throws Exception {
        return setupProject(projectName, type, component, stream, recommend, tag, description, createBaseline, forceDeliver, template, "INITIAL" );
    }

    public FreeStyleProject setupProject( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean createBaseline, boolean forceDeliver, String template, String promotionLevel ) throws Exception {
	
		logger.info( "Setting up build for self polling, recommend:" + recommend + ", tag:" + tag + ", description:" + description );
		
		System.out.println( "==== [Setting up ClearCase UCM project] ====" );
		System.out.println( " * Stream         : " + stream );
		System.out.println( " * Component      : " + component );
		System.out.println( " * Level          : " + promotionLevel );
		System.out.println( " * Polling        : " + type );
		System.out.println( " * Recommend      : " + recommend );
		System.out.println( " * Tag            : " + tag );
		System.out.println( " * Description    : " + description );
		System.out.println( " * Create baseline: " + createBaseline );
		System.out.println( " * Template       : " + template );
		System.out.println( " * Force deliver  : " + forceDeliver );
		System.out.println( "============================================" );
		
		FreeStyleProject project = createFreeStyleProject( "ccucm-project-" + projectName );
		
		// boolean createBaseline, String nameTemplate, boolean forceDeliver, boolean recommend, boolean makeTag, boolean setDescription
		//CCUCMScm scm = new CCUCMScm( component, "INITIAL", "ALL", false, type, stream, "successful", createBaseline, "[project]_build_[number]", forceDeliver, recommend, tag, description, "jenkins" );
		CCUCMScm scm = new CCUCMScm( component, promotionLevel, "ALL", false, type, stream, "successful", createBaseline, template, forceDeliver, recommend, tag, description, "", true, false );
		this.scm = scm;
		project.setScm( scm );
		
		return project;
	}
	
	public CCUCMScm getCCUCM( String type, String component, String stream, String promotionLevel, boolean recommend, boolean tag, boolean description, boolean createBaseline, boolean forceDeliver, String template ) {
		System.out.println( "==== [Setting up ClearCase UCM project] ====" );
		System.out.println( " * Stream         : " + stream );
		System.out.println( " * Component      : " + component );
		System.out.println( " * Level          : " + promotionLevel );
		System.out.println( " * Polling        : " + type );
		System.out.println( " * Recommend      : " + recommend );
		System.out.println( " * Tag            : " + tag );
		System.out.println( " * Description    : " + description );
		System.out.println( " * Create baseline: " + createBaseline );
		System.out.println( " * Template       : " + template );
		System.out.println( " * Force deliver  : " + forceDeliver );
		System.out.println( "============================================" );
		
		CCUCMScm scm = new CCUCMScm( component, promotionLevel, "ALL", false, type, stream, "successful", createBaseline, template, forceDeliver, recommend, tag, description, "", true, false );
		
		return scm;
	}
	
	public FreeStyleProject createProject( String name, CCUCMScm ccucm ) throws IOException {
		FreeStyleProject project = createFreeStyleProject( name );
		project.setScm( ccucm );
		
		return project;
	}
	
	public CCUCMScm getScm() {
		return this.scm;
	}
	
	public AbstractBuild<?, ?> initiateBuild( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean fail, boolean createBaseline ) throws Exception {
		return initiateBuild( projectName, type, component, stream, recommend, tag, description, fail, createBaseline, false );
	}
	
	public AbstractBuild<?, ?> initiateBuild( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean fail, boolean createBaseline, boolean forceDeliver ) throws Exception {
		return initiateBuild( projectName, type, component, stream, recommend, tag, description, fail, createBaseline, forceDeliver, "[project]_build_[number]" );
	}

	public AbstractBuild<?, ?> initiateBuild( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean fail, boolean createBaseline, boolean forceDeliver, String template ) throws Exception {
        return  initiateBuild(projectName, type, component, stream, recommend, tag, description, fail, createBaseline, forceDeliver, template, "INITIAL" );
    }

    public AbstractBuild<?, ?> initiateBuild( String projectName, String type, String component, String stream, boolean recommend, boolean tag, boolean description, boolean fail, boolean createBaseline, boolean forceDeliver, String template, String promotionLevel ) throws Exception {
		FreeStyleProject project = setupProject( projectName, type, component, stream, recommend, tag, description, createBaseline, forceDeliver, template, promotionLevel );
		
		FreeStyleBuild build = null;
		
		if( fail ) {
			project.getBuildersList().add(new TestBuilder() {
				@Override
			    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
			        return false;
			    }
			});
		}
		
		return buildProject( project, fail );
	}

    public ProjectBuilder getProjectBuilder( Project<?, ?> project ) {
        return new ProjectBuilder( project );
    }

    public class ProjectBuilder {
        Project<?, ?> project;

        boolean fail = false;

        boolean displayOutput = true;

        public ProjectBuilder( Project<?, ?> project ) {
            this.project = project;
        }

        public ProjectBuilder failBuild( boolean cancel ) {
            this.fail = cancel;
            return this;
        }

        public AbstractBuild build() throws ExecutionException, InterruptedException, IOException {

            if( fail ) {
                logger.info( "Failing " + project );
                project.getBuildersList().add(new Failer() );
            } else {
                /* Should remove fail task */
                project.getBuildersList().remove( Failer.class );
            }

            EnableLoggerAction action = null;
            if( outputDir != null ) {
                logger.info( "Enabling logging" );
                action = new EnableLoggerAction( outputDir );
            }

            Future<? extends Build<?, ?>> futureBuild = project.scheduleBuild2( 0, new Cause.UserCause(), action );

            AbstractBuild build = futureBuild.get();

            PrintStream out = new PrintStream( new File( outputDir, "jenkins." + getSafeName( project.getDisplayName() ) + "." + build.getNumber() + ".log" ) );

            out.println( "Build      : " + build );
            out.println( "Workspace  : " + build.getWorkspace() );
            out.println( "Logfile    : " + build.getLogFile() );
            out.println( "Description: " + build.getDescription() );
            out.println();
            out.println( "-------------------------------------------------" );
            out.println( "                JENKINS LOG: " );
            out.println( "-------------------------------------------------" );
            out.println( getLog( build ) );
            out.println( "-------------------------------------------------" );
            out.println( "-------------------------------------------------" );
            out.println();

            return build;
        }
    }

    public static class Failer extends TestBuilder {

        @Override
        public boolean perform( AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener ) throws InterruptedException, IOException {
            return false;
        }
    }

    public AbstractBuild<?, ?> buildProject( AbstractProject<?, ?> project, boolean fail ) throws IOException {
        return buildProject( project, fail, null );
    }
	
	public AbstractBuild<?, ?> buildProject( AbstractProject<?, ?> project, boolean fail, Slave slave ) throws IOException {

        if( slave != null ) {
            logger.fine( "Running on " + slave );
            project.setAssignedNode( slave );
        }

        EnableLoggerAction action = null;
        if( outputDir != null ) {
            logger.fine( "Enabling logging" );
            action = new EnableLoggerAction( outputDir );
        }

		AbstractBuild<?, ?> build = null;
		try {
			build = project.scheduleBuild2( 0, new Cause.UserCause(), action ).get();
		} catch( Exception e ) {
			logger.info( "Build failed(" + (fail?"on purpose":"it should not?") + "): " + e.getMessage() );
		}

        PrintStream out = new PrintStream( new File( outputDir, "jenkins." + getSafeName( project.getDisplayName() ) + "." + build.getNumber() + ".log" ) );

        out.println( "Build      : " + build );
        out.println( "Workspace  : " + build.getWorkspace() );
        out.println( "Logfile    : " + build.getLogFile() );
        out.println( "Description: " + build.getDescription() );
        out.println();
        out.println( "-------------------------------------------------" );
        out.println( "                JENKINS LOG: " );
        out.println( "-------------------------------------------------" );
        out.println( getLog( build ) );
        out.println( "-------------------------------------------------" );
        out.println( "-------------------------------------------------" );
        out.println();
		
		return build;
	}
	
	public void printList( List<String> list ) {
		for( String l : list ) {
			logger.fine( l );
		}
	}
	
	public CCUCMBuildAction getBuildAction( AbstractBuild<?, ?> build ) {
		/* Check the build baseline */
		logger.info( "Getting ccucm build action from " + build );
		CCUCMBuildAction action = build.getAction( CCUCMBuildAction.class );
		return action;
	}
	
	public Baseline getBuildBaseline( AbstractBuild<?, ?> build ) {
		CCUCMBuildAction action = getBuildAction( build );
		assertNotNull( action.getBaseline() );
		return action.getBaseline();
	}
	
	public Baseline getBuildBaselineNoAssert( AbstractBuild<?, ?> build ) {
		CCUCMBuildAction action = getBuildAction( build );
		return action.getBaseline();
	}

	public Baseline getCreatedBaseline( AbstractBuild<?, ?> build ) {
		CCUCMBuildAction action = getBuildAction( build );
		return action.getCreatedBaseline();
	}
	
	public void assertBuildBaseline( Baseline baseline, AbstractBuild<?, ?> build ) {
		assertEquals( baseline, getBuildBaseline( build ) );
	}
	
	public boolean isRecommended( Baseline baseline, AbstractBuild<?, ?> build ) throws ClearCaseException {
		CCUCMBuildAction action = getBuildAction( build );
		Stream stream = action.getStream().load();
		
		try {
			List<Baseline> baselines = stream.getRecommendedBaselines();
			
			logger.info( "Recommended baselines: " + baselines );
			
			for( Baseline rb : baselines ) {
				logger.fine( "BRB: " + rb );
				if( baseline.equals( rb ) ) {
					return true;
				}
			}
		} catch( Exception e ) {
			logger.fine( "" );
			ExceptionUtils.log( e, true );
		}
		
		return false;
	}
	
	public void makeTagType( PVob pvob) throws CleartoolException {
		logger.info( "Creating hyper link type TAG" );
		HyperLink.createType( Tag.__TAG_NAME, pvob, null );
	}
	
	public Tag getTag( Baseline baseline, AbstractBuild<?, ?> build ) throws ClearCaseException {
		logger.severe( "Getting tag with \"" + build.getParent().getDisplayName() + "\" - \"" + build.getNumber() + "\"" );
		logger.severe( "--->" + build.getParent().getDisplayName() );
		Tag tag = Tag.getTag( baseline, build.getParent().getDisplayName(), build.getNumber()+"", false );
		
		if( tag != null ) {
			logger.info( "TAG: " + tag.stringify() );
		} else {
			logger.info( "TAG WAS NULL" );
		}
		
		return tag;
	}
	
	public void samePromotionLevel( Baseline baseline, PromotionLevel level ) throws ClearCaseException {
		logger.info( "Current promotion level: " + baseline.getPromotionLevel( false ) );
		baseline.load();
		logger.info( "Future promotion level: " + baseline.getPromotionLevel( false ) );
		assertEquals( level, baseline.getPromotionLevel( false ) );
	}
	
	public void testCreatedBaseline( AbstractBuild<?, ?> build ) {
		CCUCMBuildAction action = getBuildAction( build );
		assertNotNull( action.getCreatedBaseline() );
	}
	
	public void testNotCreatedBaseline( AbstractBuild<?, ?> build ) {
		CCUCMBuildAction action = getBuildAction( build );
		assertNull( action.getCreatedBaseline() );
	}
		
	
	public void testLogExistence( AbstractBuild<?, ?> build ) {
		File scmLog = new File( build.getRootDir(), "ccucmSCM.log" );
		File pubLog = new File( build.getRootDir(), "ccucmNOTIFIER.log" );
		
		assertTrue( scmLog.exists() );
		assertTrue( pubLog.exists() );
	}
	
	public void testCCUCMPolling( AbstractProject<?, ?> project ) {
		File polldir = new File( project.getRootDir(), "ccucm-poll-logs" );
		
		assertTrue( polldir.exists() );
	}
	
	public List<User> getActivityUsers( AbstractBuild<?, ?> build ) {
		ChangeLogSet<? extends Entry> ls = build.getChangeSet();
		
		Object[] items = ls.getItems();
		
		System.out.println( "ITEMS: " + items );
		
		List<User> users = new ArrayList<User>();
		
		for( Object item : items ) {
			try {
				users.add( ((ChangeLogEntryImpl)item).getAuthor() );
			} catch( Exception e ) {
				
			}
		}
		
		return users;
	}
}
