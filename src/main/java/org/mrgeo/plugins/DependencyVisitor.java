package org.mrgeo.plugins;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class DependencyVisitor implements DependencyNodeVisitor
{
  private Map<String, Artifact> artifacts = new TreeMap<>();

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

  public Collection<Artifact> getArtifacts()
  {
    return artifacts.values();
  }


  @Override
  public boolean visit(DependencyNode dependencyNode)
  {
    String name = makeName(dependencyNode.getArtifact());
    artifacts.put(name, dependencyNode.getArtifact());

    return true;
  }

  @Override
  public boolean endVisit(DependencyNode dependencyNode)
  {
    return true;
  }
}
