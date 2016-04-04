package org.mrgeo.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.List;
import java.util.Properties;

/**
 * Goal to export a psuedo-effective-pom.
 *
 * @goal generate-pom
 * @requiresDependencyResolution compile
 * @phase process-sources
 */
public class GeneratePom
    extends AbstractMojo
{
  /**
   *  Maven Project
   *  @parameter default-value="${project}"
   *
   */
  private MavenProject project;

  /**
   * Project dir
   * @parameter default-value="${basedir}"
   */
  private String baseDir;


  /**
   * Location of the file
   * @parameter alias="output"
   *            default-value="${project.build.directory}/effective-pom.xml"
   */
    private File outputFile;

    public void execute()
        throws MojoExecutionException
    {
      Log log = getLog();

      try
      {
        MavenXpp3Writer writer = new MavenXpp3Writer();

        Model model = project.getModel();

        MavenProject parent = project.getParent();
        if (parent != null)
        {
          String version = parent.getVersion();

          Parent mParent = model.getParent();
          if (mParent != null)
          {
            mParent.setVersion(version);

            log.info("parent: " + mParent.toString());
          }
        }

        //model.setProperties(null);


        String basedir = model.getProjectDirectory().toString();

        Build build = model.getBuild();
        String target = build.getDirectory();


        StringWriter stringWriter = new StringWriter();
        writer.write(stringWriter, model);

        String pom = stringWriter.toString();

        String saveme = "___save_me___";
        String targettag = "<directory>" + target + "</directory>";

        // make a placeholder for the <directory> tag for the target directory
        pom = pom.replace(targettag, saveme);

        // this simply replace the fully qualified paths with the ${} equivalent
        pom = pom.replace(target, "${project.build.directory}");

        // now undo the tag for the target directory, otherwise
        pom = pom.replace(saveme, targettag);

        pom = pom.replace(basedir, "${project.basedir}");

        if (!outputFile.getParentFile().exists())
        {
          outputFile.getParentFile().mkdirs();
        }

        if (outputFile.exists())
        {
          outputFile.delete();
        }

        log.info("Writing to: " + outputFile);
        log.info("  pom length: " + pom.length());

        FileOutputStream fos = new FileOutputStream(outputFile);
        PrintWriter pw = new PrintWriter(fos);
        pw.write(pom);

        pw.close();
        fos.close();
      }
      catch (Exception e)
      {
        e.printStackTrace();
        throw new MojoExecutionException(e, "error", "error");
      }

    }
}
