package org.arend.naming.reference;

import org.arend.term.Precedence;

public class FieldReferableImpl extends DataLocatedReferableImpl implements TCFieldReferable {
  private final boolean myExplicit;
  private final boolean myParameter;

  public FieldReferableImpl(Precedence precedence, String name, boolean isExplicit, boolean isParameter, LocatedReferable parent, TCClassReferable typeClassReferable) {
    super(precedence, name, parent, typeClassReferable, Kind.FIELD);
    myExplicit = isExplicit;
    myParameter = isParameter;
  }

  @Override
  public boolean isExplicitField() {
    return myExplicit;
  }

  @Override
  public boolean isParameterField() {
    return myParameter;
  }
}
