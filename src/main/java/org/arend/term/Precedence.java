package org.arend.term;

import javax.annotation.Nonnull;

public class Precedence {
  public enum Associativity { LEFT_ASSOC, RIGHT_ASSOC, NON_ASSOC }

  public static final Precedence DEFAULT = new Precedence(Associativity.RIGHT_ASSOC, (byte) 10, false);

  public final @Nonnull Associativity associativity;
  public final byte priority;
  public final boolean isInfix;

  public Precedence(@Nonnull Associativity associativity, byte priority, boolean isInfix) {
    this.associativity = associativity;
    this.priority = priority;
    this.isInfix = isInfix;
  }

  public Precedence(byte prec) {
    this.associativity = Associativity.NON_ASSOC;
    this.priority = prec;
    this.isInfix = false;
  }

  @Override
  public String toString() {
    String result = isInfix ? "infix" : "fix";
    if (associativity == Associativity.LEFT_ASSOC) {
      result += "l";
    }
    if (associativity == Associativity.RIGHT_ASSOC) {
      result += "r";
    }
    return result + " " + priority;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Precedence that = (Precedence) o;
    return priority == that.priority && isInfix == that.isInfix && associativity == that.associativity;
  }

  @Override
  public int hashCode() {
    int result = associativity.hashCode();
    result = 31 * result + (int) priority;
    result = 31 * result + (isInfix ? 1 : 0);
    return result;
  }
}
