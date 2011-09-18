package net.praqma.hudson.remoting;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import net.praqma.clearcase.ucm.UCMException;
import net.praqma.clearcase.ucm.entities.Baseline;
import net.praqma.clearcase.ucm.entities.Component;
import net.praqma.clearcase.ucm.entities.Project.Plevel;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.clearcase.ucm.utils.BaselineList;
import net.praqma.util.debug.Logger;
import net.praqma.util.debug.appenders.StreamAppender;
import net.praqma.util.execute.CommandLine;
import hudson.FilePath.FileCallable;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;

public class GetRemoteBaselineFromStream implements FileCallable<List<Baseline>> {

	private static final long serialVersionUID = -8984877325832486334L;

	private Component component;
	private Stream stream;
	private Plevel plevel;
	private Pipe pipe;
	
	public GetRemoteBaselineFromStream( Component component, Stream stream, Plevel plevel, Pipe pipe ) {
		this.component = component;
		this.stream = stream;
		this.plevel = plevel;
		this.pipe = pipe;
    }
    
    @Override
    public List<Baseline> invoke( File f, VirtualChannel channel ) throws IOException, InterruptedException {
    	
    	Logger logger = Logger.getLogger();

    	StreamAppender app = null;
    	
    	if( pipe != null ) {
	    	PrintStream toMaster = new PrintStream( pipe.getOut() );	    	
	    	app = new StreamAppender( toMaster );
	    	Logger.addAppender( app );
    	}

        /* The baseline list */
        BaselineList baselines = null;
        
        try {
            baselines = component.getBaselines(stream, plevel );
        } catch (UCMException e) {
        	if( pipe != null ) {
        		Logger.removeAppender( app );
        	}
            throw new IOException("Could not retrieve baselines from repository. " + e.getMessage());
        }
        
    	if( pipe != null ) {
    		Logger.removeAppender( app );
    	}
        return baselines;
    }

}
