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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;

import java.io.*;
import java.util.LinkedList;
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
 * The dependency tree builder to use.
 * @component
 */
private DependencyGraphBuilder dependencyGraphBuilder;

/**
 * Location of the file
 * @parameter alias="output"
 *            default-value="${project.build.directory}/effective-pom.xml"
 */
private File outputFile;

/**
 * Location of the file
 * @parameter alias="groupId"
 *            default-value="${project.groupId}"
 */
private String groupId;

/**
 * Location of the file
 * @parameter alias="artifactId"
 *            default-value="${project.artifactId}"
 */
private String artifactId;

/**
 * Location of the file
 * @parameter alias="version"
 *            default-value="${project.version}"
 */
private String version;

    public void execute()
        throws MojoExecutionException
    {
      Log log = getLog();

      try
      {
        MavenXpp3Writer writer = new MavenXpp3Writer();

        Model model = project.getModel();

        String originalGroupId = model.getGroupId();
        String originalArtifactId = model.getArtifactId();
        String originalVersion = model.getVersion();

        if (!groupId.equals(originalGroupId))
        {
          log.info("groupId: " + groupId);
          model.setGroupId(groupId);
        }

        if (!artifactId.equals(originalArtifactId))
        {
          log.info("artifactId: " + artifactId);
          model.setArtifactId(artifactId);
        }

        if (!version.equals(originalVersion))
        {
          log.info("version: " + version);
          model.setVersion(version);
        }

        if (project.hasParent())
        {
          String parentVersion = project.getParent().getVersion();

          Parent mParent = model.getParent();
          if (mParent != null)
          {
            mParent.setVersion(parentVersion);

            log.info("parent: " + mParent.toString());
          }
        }

        //model.setProperties(null);

        String basedir = model.getProjectDirectory().toString();

        Build build = model.getBuild();
        String target = build.getDirectory();

        // get all the dependencies
        org.apache.maven.shared.dependency.graph.DependencyNode root =
            dependencyGraphBuilder.buildDependencyGraph( project, null );

        DependencyVisitor visitor = new DependencyVisitor();
        root.accept(visitor);

        // add any missing dependencies from the graph to our list.  These will be all inherited
        // dependencies not explicitly referenced out in the pom
        List<Dependency> dependencies = model.getDependencies();
        for (Artifact artifact : visitor.getArtifacts())
        {
          if (contains(artifact, dependencies))
          {
            Dependency dep = new Dependency();
            dep.setArtifactId(artifact.getArtifactId());
            dep.setGroupId(artifact.getGroupId());
            dep.setVersion(artifact.getVersion());
            dep.setClassifier(artifact.getClassifier());
            dep.setScope(artifact.getScope());
            dep.setType(artifact.getType());
            dep.setVersion(artifact.getVersion());

            dependencies.add(dep);
          }
        }

        // OK, now find any org.mrgeo dependencies and change the group version accordingly
        for (Dependency dep: dependencies)
        {
          if (dep.getGroupId().equals(originalGroupId) && dep.getVersion().equals(originalVersion))
          {
            dep.setGroupId(groupId);
            dep.setVersion(version);
          }
        }

        model.setDependencies(dependencies);

        // no need for dependencyManagement, the dependency section includes all dependencies.
        // ignore this if we don't have parents (A top-level POM)
        if (project.hasParent())
        {
          model.setDependencyManagement(null);
        }
        else
        {
          DependencyManagement dependencyManagement = model.getDependencyManagement();
          List<Dependency> dependencyManagements = dependencyManagement.getDependencies();
          for (Dependency dep: dependencyManagements)
          {
            if (dep.getGroupId().equals(originalGroupId) && dep.getVersion().equals(originalVersion))
            {
              dep.setGroupId(groupId);
              dep.setVersion(version);
            }
          }
          model.setDependencyManagement(dependencyManagement);
        }

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

private boolean contains(Artifact artifact, List<Dependency> dependencies)
{
  for (Dependency dep : dependencies)
  {
    if (artifact.getArtifactId().equals(dep.getArtifactId()) &&
        artifact.getGroupId().equals(dep.getGroupId()))
    {
      return true;
    }
  }

  return false;
}
}
