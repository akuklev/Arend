package com.jetbrains.jetpad.vclang.term.group;

import com.jetbrains.jetpad.vclang.error.SourceInfo;
import com.jetbrains.jetpad.vclang.frontend.parser.Position;
import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.term.ChildNamespaceCommand;
import com.jetbrains.jetpad.vclang.term.NameRenaming;
import com.jetbrains.jetpad.vclang.term.Precedence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public class SimpleNamespaceCommand implements ChildNamespaceCommand, SourceInfo {
  private final Position myPosition;
  private final Kind myKind;
  private final List<String> myPath;
  private final boolean myUsing;
  private final List<SimpleNameRenaming> myOpenedReferences;
  private final List<Referable> myHiddenReferences;
  private final ChildGroup myParent;

  public SimpleNamespaceCommand(Position position, Kind kind, List<String> path, boolean isUsing, List<SimpleNameRenaming> openedReferences, List<Referable> hiddenReferences, ChildGroup parent) {
    myPosition = position;
    myKind = kind;
    myPath = path;
    myUsing = isUsing;
    myOpenedReferences = openedReferences;
    myHiddenReferences = hiddenReferences;
    myParent = parent;
  }

  @Nonnull
  @Override
  public Kind getKind() {
    return myKind;
  }

  @Nonnull
  @Override
  public List<String> getPath() {
    return myPath;
  }

  @Override
  public boolean isUsing() {
    return myUsing;
  }

  @Nonnull
  @Override
  public Collection<? extends SimpleNameRenaming> getOpenedReferences() {
    return myOpenedReferences;
  }

  @Nonnull
  @Override
  public Collection<? extends Referable> getHiddenReferences() {
    return myHiddenReferences;
  }

  @Override
  public String moduleTextRepresentation() {
    return myPosition.moduleTextRepresentation();
  }

  @Override
  public String positionTextRepresentation() {
    return myPosition.positionTextRepresentation();
  }

  @Nullable
  @Override
  public ChildGroup getParentGroup() {
    return myParent;
  }

  public static class SimpleNameRenaming implements NameRenaming, SourceInfo {
    private final Position myPosition;
    private final Referable myReference;
    private final Precedence myPrecedence;
    private final String myName;

    public SimpleNameRenaming(Position position, Referable reference, Precedence precedence, String name) {
      myPosition = position;
      myReference = reference;
      myPrecedence = precedence;
      myName = name;
    }

    @Nonnull
    @Override
    public Referable getOldReference() {
      return myReference;
    }

    @Nullable
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public Precedence getPrecedence() {
      return myPrecedence;
    }

    @Override
    public String moduleTextRepresentation() {
      return myPosition.moduleTextRepresentation();
    }

    @Override
    public String positionTextRepresentation() {
      return myPosition.positionTextRepresentation();
    }
  }
}