package org.arend.library;

import org.arend.error.ErrorReporter;
import org.arend.module.ModulePath;
import org.arend.naming.reference.converter.IdReferableConverter;
import org.arend.naming.reference.converter.ReferableConverter;
import org.arend.source.BinarySource;
import org.arend.source.Source;
import org.arend.source.SourceLoader;
import org.arend.source.error.PersistingError;
import org.arend.term.group.ChildGroup;
import org.arend.typechecking.TypecheckerState;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.typechecking.order.dependency.DummyDependencyListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Represents a library which can load modules in the binary format (see {@link #getBinarySource})
 * as well as ordinary modules (see {@link #getRawSource}).
 */
public abstract class SourceLibrary extends BaseLibrary {
  public enum Flag { RECOMPILE }
  private final EnumSet<Flag> myFlags = EnumSet.noneOf(Flag.class);

  /**
   * Creates a new {@code SourceLibrary}
   *
   * @param typecheckerState  the underling typechecker state of this library.
   */
  protected SourceLibrary(TypecheckerState typecheckerState) {
    super(typecheckerState);
  }

  /**
   * Adds a flag.
   */
  public void addFlag(Flag flag) {
    myFlags.add(flag);
  }

  /**
   * Removes a flag.
   */
  public void removeFlag(Flag flag) {
    myFlags.remove(flag);
  }

  /**
   * Gets the raw source (that is, the source containing not typechecked data) for a given module path.
   *
   * @param modulePath  a path to the source.
   *
   * @return the raw source corresponding to the given path or null if the source is not found.
   */
  @Nullable
  public abstract Source getRawSource(ModulePath modulePath);

  /**
   * Gets the binary source (that is, the source containing typechecked data) for a given module path.
   *
   * @param modulePath  a path to the source.
   *
   * @return the binary source corresponding to the given path or null if the source is not found.
   */
  @Nullable
  public abstract BinarySource getBinarySource(ModulePath modulePath);

  /**
   * Loads the header of this library.
   *
   * @param errorReporter a reporter for all errors that occur during the loading process.
   *
   * @return loaded library header, or null if some error occurred.
   */
  @Nullable
  protected abstract LibraryHeader loadHeader(ErrorReporter errorReporter);

  /**
   * Invoked by a source after it loads the group of a module.
   *
   * @param modulePath  the path to the loaded module.
   * @param group       the group of the loaded module or null if the group was not loaded.
   * @param isRaw       true if the module was loaded from a raw source, false otherwise.
   */
  public void onGroupLoaded(ModulePath modulePath, @Nullable ChildGroup group, boolean isRaw) {

  }

  /**
   * Invoked by a binary source after it is loaded.
   *
   * @param modulePath  the path to the loaded module.
   * @param isComplete  true if the module was loaded completely, false otherwise.
   */
  public void onBinaryLoaded(ModulePath modulePath, boolean isComplete) {

  }

  /**
   * Checks if this library has any raw sources.
   * Note that currently libraries without raw sources do not work properly with class synonyms.
   *
   * @return true if the library has raw sources, false otherwise.
   */
  public boolean hasRawSources() {
    return true;
  }

  /**
   * Gets a referable converter which is used during loading of binary sources without raw counterparts.
   *
   * @return a referable converter or null if the library does not have raw sources.
   */
  @Nullable
  public ReferableConverter getReferableConverter() {
    return IdReferableConverter.INSTANCE;
  }

  /**
   * Gets a dependency listener for definitions loaded from binary sources.
   *
   * @return a dependency listener.
   */
  @Nonnull
  public DependencyListener getDependencyListener() {
    return DummyDependencyListener.INSTANCE;
  }

  @Override
  public boolean load(LibraryManager libraryManager) {
    if (isLoaded()) {
      return true;
    }

    LibraryHeader header = loadHeader(libraryManager.getLibraryErrorReporter());
    if (header == null) {
      return false;
    }

    for (LibraryDependency dependency : header.dependencies) {
      Library loadedDependency = libraryManager.loadLibrary(dependency.name);
      if (loadedDependency == null) {
        return false;
      }
      libraryManager.registerDependency(this, loadedDependency);
    }

    SourceLoader sourceLoader = new SourceLoader(this, libraryManager);
    if (hasRawSources()) {
      for (ModulePath module : header.modules) {
        sourceLoader.preloadRaw(module);
      }
      sourceLoader.loadRawSources();
    }

    if (!myFlags.contains(Flag.RECOMPILE)) {
      for (ModulePath module : header.modules) {
        sourceLoader.loadBinary(module);
      }
    }

    return super.load(libraryManager);
  }

  @Override
  public boolean containsModule(ModulePath modulePath) {
    Source source = getRawSource(modulePath);
    if (source != null && source.isAvailable()) {
      return true;
    }
    source = getBinarySource(modulePath);
    return source != null && source.isAvailable();
  }

  public boolean supportsPersisting() {
    return true;
  }

  public boolean persistModule(ModulePath modulePath, ReferableConverter referableConverter, ErrorReporter errorReporter) {
    BinarySource source = getBinarySource(modulePath);
    if (source == null) {
      errorReporter.report(new PersistingError(modulePath));
      return false;
    } else {
      return source.persist(this, referableConverter, errorReporter);
    }
  }

  public boolean deleteModule(ModulePath modulePath) {
    BinarySource source = getBinarySource(modulePath);
    return source != null && source.delete(this);
  }
}
