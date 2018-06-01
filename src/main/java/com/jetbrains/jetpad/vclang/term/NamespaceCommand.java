package com.jetbrains.jetpad.vclang.term;

import com.jetbrains.jetpad.vclang.error.doc.DocFactory;
import com.jetbrains.jetpad.vclang.error.doc.DocStringBuilder;
import com.jetbrains.jetpad.vclang.error.doc.LineDoc;
import com.jetbrains.jetpad.vclang.module.ModulePath;
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable;
import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrintable;
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.jetbrains.jetpad.vclang.error.doc.DocFactory.*;

public interface NamespaceCommand extends PrettyPrintable {
  enum Kind { OPEN, IMPORT }
  @Nonnull Kind getKind();
  @Nonnull List<String> getPath();
  boolean isUsing();
  @Nonnull Collection<? extends NameRenaming> getOpenedReferences();
  @Nonnull Collection<? extends Referable> getHiddenReferences();

  @Override
  default void prettyPrint(StringBuilder builder, PrettyPrinterConfig infoProvider) {
    List<String> path = getPath();
    if (path.isEmpty()) {
      return;
    }

    List<LineDoc> docs = new ArrayList<>();
    switch (getKind()) {
      case OPEN: docs.add(text("\\open")); break;
      case IMPORT: docs.add(text("\\import")); break;
    }

    List<LineDoc> docPath = new ArrayList<>(path.size());
    for (int i = 1; i <= path.size(); i++) {
      docPath.add(refDoc(new ModuleReferable(new ModulePath(path.subList(0, i)))));
    }
    LineDoc referenceDoc = hSep(text("."), docPath);

    Collection<? extends NameRenaming> openedReferences = getOpenedReferences();
    boolean using = isUsing();
    if (!using || !openedReferences.isEmpty()) {
      List<LineDoc> renamingDocs = new ArrayList<>(openedReferences.size());
      for (NameRenaming renaming : openedReferences) {
        LineDoc renamingDoc = refDoc(renaming.getOldReference());
        String newName = renaming.getName();
        if (newName != null) {
          Precedence precedence = renaming.getPrecedence();
          renamingDoc = hList(renamingDoc, text(" \\as " + (precedence == null ? "" : precedence)), text(newName));
        }
        renamingDocs.add(renamingDoc);
      }

      LineDoc openedReferencesDoc = hSep(text(", "), renamingDocs);
      if (!using) {
        referenceDoc = hList(referenceDoc, text("("), openedReferencesDoc, text(")"));
      }
      docs.add(referenceDoc);
      if (using) {
        docs.add(text("\\using"));
        docs.add(openedReferencesDoc);
      }
    } else {
      docs.add(referenceDoc);
    }

    Collection<? extends Referable> hidingReferences = getHiddenReferences();
    if (!hidingReferences.isEmpty()) {
      docs.add(text("\\hiding"));
      docs.add(hList(text("("), hSep(text(", "), hidingReferences.stream().map(DocFactory::refDoc).collect(Collectors.toList())), text(")")));
    }

    DocStringBuilder.build(builder, hSep(text(" "), docs));
  }
}