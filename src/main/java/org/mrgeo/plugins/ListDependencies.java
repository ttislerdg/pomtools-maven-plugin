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
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;

import java.io.*;
import java.util.List;

/**
 * Goal to list dependencies in a organized manner.
 *
 * @goal list-dependencies
 * @requiresDependencyResolution compile
 * @phase generate-sources
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class ListDependencies
    extends AbstractMojo
{

  /**
   * The entry point to Aether, i.e. the component doing all the work.
   * @parameter default-value="${project.repositorySystem}"
   * @readonly
   * @component
   */
  private RepositorySystem repoSystem;

  /**
   * The current repository/network configuration of Maven.
   *
   * @parameter default-value="${repositorySystemSession}"
   * @readonly
   */
  private RepositorySystemSession repoSession;

  /**
   * The project's remote repositories to use for the resolution of project dependencies.
   *
   * @parameter default-value="${project.remoteProjectRepositories}"
   * @readonly
   */
  private List<RemoteRepository> projectRepos;

  /**
   * The project's remote repositories to use for the resolution of plugins and their dependencies.
   *
   * @parameter default-value="${project.remotePluginRepositories}"
   * @readonly
   */
  private List<RemoteRepository> pluginRepos;

  /**
   * The dependency tree builder to use.
   * @component
   */
  private DependencyGraphBuilder dependencyGraphBuilder;

  /**
   *  Maven Project
   *  @parameter default-value="${project}"
   *
   */
  private MavenProject project;


  /**
   * Location of the file
   * @parameter alias="output"
   *            default-value="${project.build.directory}/dependencies.properties"
   */
  private File outputFile;

  /**
   * Location of the file
   * @parameter alias="separator"
   *            default-value="|"
   */
  private String separator;

  public ListDependencies()
  {
  }

  private MavenProject loadProject(File pomFile) throws Exception
  {
    MavenProject ret = null;
    MavenXpp3Reader mavenReader = new MavenXpp3Reader();

    if (pomFile != null && pomFile.exists())
    {
      FileReader reader = null;

      try
      {
        reader = new FileReader(pomFile);
        Model model = mavenReader.read(reader);
        model.setPomFile(pomFile);

        ret = new MavenProject(model);
      }
      finally
      {
        // Close reader
      }
    }

    return ret;
  }

  private String makeName(Artifact artifact)
  {
    if (artifact.getFile() != null)
    {
      return artifact.getFile().getName();
    }
    else
    {
      StringBuilder name = new StringBuilder();

      name.append(artifact.getArtifactId()).append("-").append(artifact.getVersion());

      if (artifact.getClassifier() != null && artifact.getClassifier().length() > 0)
      {
        name.append("-").append(artifact.getClassifier());
      }

      if (artifact.isSnapshot() && !name.toString().endsWith("SNAPSHOT"))
      {
        name.append("-SNAPSHOT");
      }

      return name.toString();
    }
  }

  private String makeFile(Artifact artifact)
  {
    if (artifact.getFile() != null)
    {
      return artifact.getFile().getName();
    }
    else
    {
      StringBuilder name = new StringBuilder();

      name.append(artifact.getArtifactId()).append("-").append(artifact.getVersion());

      if (artifact.getClassifier() != null && artifact.getClassifier().length() > 0)
      {
        name.append("-").append(artifact.getClassifier());
      }

      if (artifact.isSnapshot() && !name.toString().endsWith("SNAPSHOT"))
      {
        name.append("-SNAPSHOT");
      }

      name.append(".").append(artifact.getType());

      return name.toString();
    }
  }

  public void execute()
      throws MojoExecutionException
  {
    Log log = getLog();

    try
    {
      Model model = project.getModel();

      if (!outputFile.getParentFile().exists())
      {
        outputFile.getParentFile().mkdirs();
      }

      if (outputFile.exists())
      {
        outputFile.delete();
      }

      log.info("Writing to: " + outputFile);

      FileOutputStream fos = new FileOutputStream(outputFile);
      PrintWriter pw = new PrintWriter(fos);

      pw.println("# Main Jar: ");
      pw.println("#  GroupId | ArtifactId | Version | Name");

      pw.print(model.getGroupId() + separator +
          model.getArtifactId() + separator +
          model.getVersion() + separator);
      pw.println(model.getArtifactId() + "-" + model.getVersion() + "." + model.getPackaging());

      pw.println("\n\n# Dependencies: ");
      pw.println("#  GroupId | ArtifactId | Version | Classifier | Type | Scope | Name");


      try
      {
        org.apache.maven.shared.dependency.graph.DependencyNode root =
            dependencyGraphBuilder.buildDependencyGraph( project, null );

//        StringWriter writer = new StringWriter();
//
//        SerializingDependencyNodeVisitor visitor = new SerializingDependencyNodeVisitor(writer);
//        root.accept(visitor);
//
//        log.info(writer.toString());
        DependencyVisitor visitor = new DependencyVisitor();
        root.accept(visitor);

        for (Artifact artifact : visitor.getArtifacts())
        {
          {
            log.debug(artifact.getGroupId() + separator +
                artifact.getArtifactId() + separator +
                artifact.getVersion() + separator +
                (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
                artifact.getType() + separator +
                artifact.getScope() + separator +
                makeFile(artifact));

            pw.println(artifact.getGroupId() + separator +
                artifact.getArtifactId() + separator +
                artifact.getVersion() + separator +
                (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
                artifact.getType() + separator +
                artifact.getScope() + separator +
                makeFile(artifact));
          }
        }

      }
      catch (DependencyGraphBuilderException e)
      {
        e.printStackTrace();
      }


//      Map<String, Artifact> artifacts = new TreeMap<>();
//      Map<String, String> scopes = new HashMap<>();
//
//      for (org.apache.maven.model.Dependency dep : model.getDependencies())
//      {
//        //Artifact root = new DefaultArtifact(model.getGroupId(), model.getArtifactId(), model.getPackaging(), model.getVersion());
//        Artifact root =
//            new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(), dep.getVersion());
//
//        artifacts.put(makeName(root), root);
//        scopes.put(makeName(root), dep.getScope());
//
//        for (String scope : new String[]{"compile", "runtime"})
//        {
//          try
//          {
//            Dependency dependency = new Dependency(root, scope);
//            CollectRequest collectRequest = new CollectRequest();
//            collectRequest.setRoot(dependency);
//
//            // this will collect the transitive dependencies of an artifact and build a dependency graph
//            DependencyNode node = repoSystem.collectDependencies(repoSession, collectRequest).getRoot();
//            DependencyRequest dependencyRequest = new DependencyRequest();
//
//            dependencyRequest.setRoot(node);
//
//            // this will collect and resolve the transitive dependencies of an artifact
//            DependencyResult depRes = repoSystem.resolveDependencies(repoSession, dependencyRequest);
//
//            List<ArtifactResult> results = depRes.getArtifactResults();
//
//            for (ArtifactResult result : results)
//            {
//              Artifact artifact = result.getArtifact();
//
//              ArtifactRequest artifactRequest = new ArtifactRequest();
//              artifactRequest.setArtifact(artifact);
//
//              try
//              {
//                ArtifactResult ar = repoSystem.resolveArtifact(repoSession, artifactRequest);
//
//                Artifact a = ar.getArtifact();
//                log.info("###" + a.toString());
//              }
//              catch (ArtifactResolutionException e)
//              {
//                e.printStackTrace();
//              }
//
//              artifacts.put(makeName(artifact), artifact);
//              scopes.put(makeName(artifact), scope);
//
//              if (artifact.getGroupId().equals("org.apache.avro"))
//              {
//                log.info("***" + artifact.getGroupId() + separator +
//                    artifact.getArtifactId() + separator +
//                    artifact.getVersion() + separator +
//                    (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
//                    artifact.getExtension() + separator +
//                    scopes.get(makeName(artifact)) + separator +
//                    makeFile(artifact) + " *** " + makeName(root));
//
//              }
//            }
//          }
//          catch (DependencyCollectionException | DependencyResolutionException e)
//          {
//            // no op
//          }
//        }
//      }
//
//      for (Artifact artifact : artifacts.values())
//      {
//        {
//          log.debug(artifact.getGroupId() + separator +
//              artifact.getArtifactId() + separator +
//              artifact.getVersion() + separator +
//              (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
//              artifact.getExtension() + separator +
//              scopes.get(makeName(artifact)) + separator +
//              makeFile(artifact));
//
//          pw.println(artifact.getGroupId() + separator +
//              artifact.getArtifactId() + separator +
//              artifact.getVersion() + separator +
//              (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
//              artifact.getExtension() + separator +
//              scopes.get(makeName(artifact)) + separator +
//              makeFile(artifact));
//        }
//      }

//      String[] scopes = new String[]{MavenClasspath.COMPILE_SCOPE, MavenClasspath.RUNTIME_SCOPE};

      //DefaultDependencyGraphBuilder builder = new DefaultDependencyGraphBuilder();

      //dumpTree(node);

//      builder.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "DefaultDependencyGraphBuilder" ));
//
//      log.info(builder.toString());
//      log.info(project.getId());
//
//
//      DependencyNode node = builder.buildDependencyGraph(project, null);
//
//      MavenClasspath mcp = new MavenClasspath(builder, mavenSession, Arrays.asList(scopes));
//      for (File jar: mcp)
//      {
//        log.info(jar.getCanonicalPath());
//      }
//
//      Classpath jars = new Classpath(
//          project,
//          session.getLocalRepository().getBasedir(), Arrays.asList(scopes)
//      );
//
//      for (File jar: jars)
//      {
//        log.info(jar.getCanonicalPath());
//      }

//      Aether aether = new Aether(project, session.getLocalRepository().getBasedir());
//      for (Dependency dep : model.getDependencies())
//      {
//        Artifact root = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getType(),
//            dep.getVersion());
//        String rootName = dep.getArtifactId() + "-" + dep.getVersion() + "." + dep.getType();
//        for (String scope : new String[]{"compile", "runtime"})
//        {
//          for (Artifact artifact : aether.resolve(root, scope))
//          {
//            // if (!artifact.getFile().getName().equals(rootName))
//            {
//            log.info(artifact.getGroupId() + separator +
//                artifact.getArtifactId() + separator +
//                artifact.getVersion() + separator +
//                (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
//                artifact.getExtension() + separator +
//                scope + separator +
//                artifact.getFile().getName());
//
//              pw.println(artifact.getGroupId() + separator +
//                  artifact.getArtifactId() + separator +
//                  artifact.getVersion() + separator +
//                  (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
//                  artifact.getExtension() + separator +
//                  scope + separator +
//                  artifact.getFile().getName());
//            }
//          }
//        }
//
//      }

//      Artifact root = new DefaultArtifact(model.getGroupId(), model.getArtifactId(), model.getPackaging(), model.getVersion());
//      String rootName = model.getArtifactId() + "-" + model.getVersion() + "." + model.getPackaging();
//
//
//      log.info("***" + session.getLocalRepository().getBasedir());
//      Aether aether = new Aether(project, session.getLocalRepository().getBasedir());
//      for (String scope : new String[]{"compile", "runtime"})
//      {
//        for (Artifact artifact : aether.resolve(root, scope))
//        {
//          if (!artifact.getFile().getName().equals(rootName))
//          {
////            log.info(artifact.getGroupId() + separator +
////                artifact.getArtifactId() + separator +
////                artifact.getVersion() + separator +
////                (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
////                artifact.getExtension() + separator +
////                scope + separator +
////                artifact.getFile().getName());
//
//            pw.println(artifact.getGroupId() + separator +
//                artifact.getArtifactId() + separator +
//                artifact.getVersion() + separator +
//                (artifact.getClassifier() != null ? artifact.getClassifier() : "") + separator +
//                artifact.getExtension() + separator +
//                scope + separator +
//                artifact.getFile().getName());
//          }
//        }
//      }

      pw.close();
      fos.close();
    }
    catch (IOException e)
    {
      e.printStackTrace();
      throw new MojoExecutionException(e, "IOException", "IOException");

    }

  }
}
