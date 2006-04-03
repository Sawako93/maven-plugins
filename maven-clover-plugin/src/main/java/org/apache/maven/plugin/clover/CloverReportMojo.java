/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.plugin.clover;

import com.cenqua.clover.reporters.html.HtmlReporter;
import com.cenqua.clover.reporters.pdf.PDFReporter;
import com.cenqua.clover.reporters.xml.XMLReporter;

import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.clover.internal.AbstractCloverMojo;

import java.io.File;
import java.util.*;

/**
 * Generate a <a href="http://cenqua.com/clover">Clover</a> report from existing Clover databases. The generated report
 * is an external report generated by Clover itself. If the project generating the report is a top level project and
 * if the <code>aggregate</code> configuration element is set to true then an aggregated report will also be created.
 *
 * Note: This report mojo should be an @aggregator and the <code>clover:aggregate</code> mojo shouldn't exist. This
 * is a limitation of the site plugin which doesn't support @aggregator reports...
 *
 * @goal clover
 *
 * @author <a href="mailto:vmassol@apache.org">Vincent Massol</a>
 * @version $Id$
 */
public class CloverReportMojo extends AbstractMavenReport
{
    // TODO: Need some way to share config elements and code between report mojos and main build
    // mojos. See http://jira.codehaus.org/browse/MNG-1886

    /**
     * The location of the <a href="http://cenqua.com/clover/doc/adv/database.html">Clover database</a>.
     *
     * @parameter expression="${project.build.directory}/clover/clover.db"
     * @required
     */
    private String cloverDatabase;

    /**
     * The location of the merged clover database to create when running a report in a multimodule build.
     *
     * @parameter expression="${project.build.directory}/clover/cloverMerge.db"
     * @required
     */
    private String cloverMergeDatabase;

    /**
     * The directory where the Clover report will be generated.
     *
     * @parameter expression="${project.reporting.outputDirectory}/clover"
     * @required
     */
    private File outputDirectory;

    /**
     * When the Clover Flush Policy is set to "interval" or threaded this value is the minimum
     * period between flush operations (in milliseconds).
     *
     * @parameter default-value="500"
     */
    private int flushInterval;

    /**
     * If true we'll wait 2*flushInterval to ensure coverage data is flushed to the Clover
     * database before running any query on it.
     *
     * Note: The only use case where you would want to turn this off is if you're running your
     * tests in a separate JVM. In that case the coverage data will be flushed by default upon
     * the JVM shutdown and there would be no need to wait for the data to be flushed. As we
     * can't control whether users want to fork their tests or not, we're offering this parameter
     * to them.
     *
     * @parameter default-value="true"
     */
    private boolean waitForFlush;

    /**
     * Decide whether to generate an HTML report
     * @parameter default-value="true"
     */
    private boolean generateHtml;

    /**
     * Decide whether to generate a PDF report
     * @parameter default-value="false"
     */
    private boolean generatePdf;

    /**
     * Decide whether to generate a XML report
     * @parameter default-value="false"
     */
    private boolean generateXml;

    /**
     * @component
     */
    private Renderer siteRenderer;

    /**
     * The Maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The projects in the reactor for aggregation report.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List reactorProjects;

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#executeReport(java.util.Locale)
     */
    public void executeReport( Locale locale ) throws MavenReportException
    {
        // Ensure the output directory exists
        this.outputDirectory.mkdirs();

        File singleModuleCloverDatabase = new File( this.cloverDatabase );
        if ( singleModuleCloverDatabase.exists() )
        {
            if ( this.generateHtml )
            {
                createHtmlReport();
            }
            if ( this.generatePdf )
            {
                createPdfReport();
            }
            if ( this.generateXml )
            {
                createXmlReport();
            }
        }

        File mergedCloverDatabase = new File ( this.cloverMergeDatabase );
        if ( mergedCloverDatabase.exists() )
        {
            if ( this.generateHtml )
            {
                createMasterHtmlReport();
            }
            if ( this.generatePdf )
            {
                createMasterPdfReport();
            }
            if ( this.generateXml )
            {
                createMasterXmlReport();
            }
        }
    }

    private List getCommonCliArgs( File reportOutputFile )
    {
        List parameters = new ArrayList();

        parameters.add( "-t" );
        parameters.add( "Maven Clover report" );
        parameters.add( "-i" );
        parameters.add( this.cloverDatabase );
        parameters.add( "-o" );
        parameters.add( reportOutputFile.getPath() );

        if ( getLog().isDebugEnabled() )
        {
            parameters.add( "-d" );
        }

        return parameters;
    }

    /**
     * @todo handle multiple source roots. At the moment only the first source root is instrumented
     */
    private void createHtmlReport() throws MavenReportException
    {
        List parameters = getCommonCliArgs( this.outputDirectory );

        parameters.add( "-p" );
        parameters.add( this.project.getCompileSourceRoots().get( 0 ) );

        createReport(HtmlReporter.class, parameters, "HTML");
    }

    private void createPdfReport() throws MavenReportException
    {
        createReport(PDFReporter.class, getCommonCliArgs( new File( this.outputDirectory, "clover.pdf" ) ), "PDF");
    }

    private void createXmlReport() throws MavenReportException
    {
        createReport(XMLReporter.class, getCommonCliArgs( new File( this.outputDirectory, "clover.xml" ) ), "XML");
    }

    private void createReport( Class reportClass, List parameters, String reportType )
        throws MavenReportException
    {
        int result;
        try
        {
            result = ((Integer) reportClass.getMethod( "mainImpl", new Class[]{String[].class}).invoke(null,
                new Object[]{(String[]) parameters.toArray( new String[0] )} )).intValue();
        }
        catch (Exception e)
        {
            throw new MavenReportException( "Failed to call [" + reportClass.getName() + ".mainImpl]", e );
        }

        if ( result != 0 )
        {
            throw new MavenReportException( "Clover has failed to create the " + reportType + " report" );
        }
    }

    private List getCommonCliArgsForMasterReport( File reportOutputFile )
    {
        List parameters = new ArrayList();

        parameters.add( "-t" );
        parameters.add( "Maven Aggregated Clover report" );
        parameters.add( "-i" );
        parameters.add( this.cloverMergeDatabase );
        parameters.add( "-o" );
        parameters.add( reportOutputFile.getPath() );

        if ( getLog().isDebugEnabled() )
        {
            parameters.add( "-d" );
        }

        return parameters;
    }

    private void createMasterHtmlReport() throws MavenReportException
    {
        createReport(HtmlReporter.class, getCommonCliArgsForMasterReport( this.outputDirectory ), "merged HTML");
    }

    private void createMasterPdfReport() throws MavenReportException
    {
        createReport(PDFReporter.class, getCommonCliArgsForMasterReport(
            new File( this.outputDirectory, "cloverMerged.pdf" ) ), "merged PDF");
    }

    private void createMasterXmlReport() throws MavenReportException
    {
        createReport(XMLReporter.class, getCommonCliArgsForMasterReport(
            new File( this.outputDirectory, "cloverMerged.xml" ) ), "merged XML");
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getOutputName()
     */
    public String getOutputName()
    {
        return "clover/index";
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription( Locale locale )
    {
        return getBundle( locale ).getString( "report.clover.description" );
    }

    private static ResourceBundle getBundle( Locale locale )
    {
        return ResourceBundle.getBundle( "clover-report", locale, CloverReportMojo.class.getClassLoader() );
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getOutputDirectory()
     */
    protected String getOutputDirectory()
    {
        return this.outputDirectory.getAbsoluteFile().toString();
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getSiteRenderer()
     */
    protected Renderer getSiteRenderer()
    {
        return this.siteRenderer;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getProject()
     */
    protected MavenProject getProject()
    {
        return this.project;
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName( Locale locale )
    {
        return getBundle( locale ).getString( "report.clover.name" );
    }

    /**
     * Always return true as we're using the report generated by Clover rather than creating our own report.
     * @return true
     */
    public boolean isExternalReport()
    {
        return true;
    }

    /**
     * Generate reports if a Clover module database or a Clover merged database exist.
     *
     * @return true if a project should be generated
     * @see org.apache.maven.reporting.AbstractMavenReport#canGenerateReport()
     */
    public boolean canGenerateReport()
    {
        boolean canGenerate = false;

        AbstractCloverMojo.waitForFlush( this.waitForFlush, this.flushInterval );

        File singleModuleCloverDatabase = new File( this.cloverDatabase );
        File mergedCloverDatabase = new File ( this.cloverMergeDatabase );

        if (singleModuleCloverDatabase.exists() || mergedCloverDatabase.exists() )
        {
            if ( this.generateHtml || this.generatePdf || this.generateXml )
            {
                canGenerate = true;
            }
        }
        else
        {
            getLog().warn("No Clover database found, skipping report generation");
        }

        return canGenerate;
    }
}
