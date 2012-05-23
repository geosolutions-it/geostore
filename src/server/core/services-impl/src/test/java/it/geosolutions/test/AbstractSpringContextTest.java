package it.geosolutions.test;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Base class for tests with a spring context loaded from the classpath.
 *
 * @author Nate Sammons
 */
public abstract class AbstractSpringContextTest extends TestCase {

    protected Logger logger = Logger.getLogger(getClass());
    protected ClassPathXmlApplicationContext context = null;

    /**
     * Get the filename to use for this context.
     */
    protected abstract String[] getContextFilenames();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        context = new ClassPathXmlApplicationContext(getContextFilenames());
        logger.info("Built test context: " + context);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        logger.info("Closing test context");
        context.close();
        context = null;
    }
}
