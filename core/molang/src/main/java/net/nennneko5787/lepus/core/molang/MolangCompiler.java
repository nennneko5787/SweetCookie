package net.nennneko5787.lepus.core.molang;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.molang.MolangExpr.Op;
import net.nennneko5787.lepus.core.molang.MolangLexer.Kind;
import net.nennneko5787.lepus.core.molang.MolangLexer.Token;

/**
 * Recursive-descent parser that emits closures instead of an AST. SC-130 §2, ADR-0013.
 *
 * <p>Folding happens as the tree is built: an operation whose operands are all constant is replaced
 * by the constant it evaluates to, so {@code math.floor(2.5) * 4} costs nothing at runtime. That is
 * the first of SC-130 §6's techniques and the cheapest.
 *
 * <p><b>Everything is {@code float}.</b> Not the result — everything. Intermediates too, because a
 * comparison sees intermediates and {@code 0.1 + 0.2 > 0.3} answers differently in the two widths.
 */
@SpecImpl({"SC-130", "SC-130#syntax/arithmetic", "SC-130#syntax/comparison"})
final class MolangCompiler {

    private final String source;
    private final List<Token> tokens;
    private final Set<String> queries = new LinkedHashSet<>();
    private final Set<String> unresolved = new LinkedHashSet<>();
    private int at;

    MolangCompiler(String source) {
        this.source = source;
        this.tokens = new MolangLexer(source).tokenise();
    }

    MolangExpr compile() {
        Node program = parseProgram();
        return new MolangExpr(
                program.op(), source, program.isConstant(), program.constantOrZero(),
                queries, unresolved);
    }

    /**
     * A compiled subexpression, plus whether it folded.
     *
     * <p>Carrying constness alongside the closure is what makes folding one line at each site rather
     * than a separate pass over an AST that no longer exists.
     */
    private record Node(Op op, Float constant) {

        static Node of(float value) {
            return new Node(ctx -> value, value);
        }

        static Node dynamic(Op op) {
            return new Node(op, null);
        }

        boolean isConstant() {
            return constant != null;
        }

        float constantOrZero() {
            return constant == null ? 0f : constant;
        }
    }

    /** Folds when both operands are constant; otherwise emits the closure. */
    private static Node binary(Node left, Node right, java.util.function.DoubleBinaryOperator ignored,
            FloatBinary fn) {
        if (left.isConstant() && right.isConstant()) {
            return Node.of(fn.apply(left.constantOrZero(), right.constantOrZero()));
        }
        Op l = left.op();
        Op r = right.op();
        return Node.dynamic(ctx -> fn.apply(l.apply(ctx), r.apply(ctx)));
    }

    @FunctionalInterface
    private interface FloatBinary {
        float apply(float a, float b);
    }

    // ── Statements ───────────────────────────────────────────────────────────────────────────

    /**
     * A whole expression body. SC-130 §2.3.
     *
     * <p>Bedrock requires a multi-statement body to end in {@code return}. Real packs omit it, so
     * the value of the last statement is used when none appears — refusing would cost the pack an
     * expression over a missing keyword that Bedrock itself tolerates.
     */
    private Node parseProgram() {
        List<Node> statements = new ArrayList<>();
        boolean sawReturn = false;
        while (!check(Kind.END)) {
            if (match(Kind.RETURN)) {
                statements.add(parseAssignment());
                sawReturn = true;
                match(Kind.SEMICOLON);
                break;
            }
            statements.add(parseAssignment());
            if (!match(Kind.SEMICOLON)) {
                break;
            }
        }
        expect(Kind.END, "expected end of expression");
        if (statements.isEmpty()) {
            return Node.of(0f);
        }
        if (statements.size() == 1) {
            return statements.get(0);
        }
        // Only the last statement's value is the result; the earlier ones run for their assignments.
        List<Op> ops = statements.stream().map(Node::op).toList();
        int last = ops.size() - 1;
        boolean allConstant = !sawReturn && statements.stream().allMatch(Node::isConstant);
        if (allConstant) {
            return Node.of(statements.get(last).constantOrZero());
        }
        return Node.dynamic(ctx -> {
            float value = 0f;
            for (int i = 0; i <= last; i++) {
                value = ops.get(i).apply(ctx);
            }
            return value;
        });
    }

    private Node parseAssignment() {
        int mark = at;
        if (check(Kind.IDENTIFIER)) {
            MolangContext.Scope scope = scopeOf(peek().text());
            if (scope == MolangContext.Scope.VARIABLE || scope == MolangContext.Scope.TEMP) {
                advance();
                if (match(Kind.DOT) && check(Kind.IDENTIFIER)) {
                    String name = advance().text();
                    if (match(Kind.ASSIGN)) {
                        Node value = parseAssignment();
                        Op v = value.op();
                        // An assignment is never constant: its effect is the write.
                        return Node.dynamic(ctx -> {
                            float result = v.apply(ctx);
                            ctx.write(scope, name, result);
                            return result;
                        });
                    }
                }
            }
        }
        at = mark;
        return parseTernary();
    }

    // ── Operators, lowest precedence first ───────────────────────────────────────────────────

    private Node parseTernary() {
        Node condition = parseCoalesce();
        if (!match(Kind.QUESTION)) {
            return condition;
        }
        Node whenTrue = parseAssignment();
        if (match(Kind.COLON)) {
            Node whenFalse = parseAssignment();
            if (condition.isConstant()) {
                return truthy(condition.constantOrZero()) ? whenTrue : whenFalse;
            }
            Op c = condition.op();
            Op t = whenTrue.op();
            Op f = whenFalse.op();
            return Node.dynamic(ctx -> truthy(c.apply(ctx)) ? t.apply(ctx) : f.apply(ctx));
        }
        // Binary-if: `a ? b` yields b when a is true and 0 otherwise. Bedrock's, and a construct
        // packs use constantly for "add this offset only while flying".
        if (condition.isConstant()) {
            return truthy(condition.constantOrZero()) ? whenTrue : Node.of(0f);
        }
        Op c = condition.op();
        Op t = whenTrue.op();
        return Node.dynamic(ctx -> truthy(c.apply(ctx)) ? t.apply(ctx) : 0f);
    }

    private Node parseCoalesce() {
        Node left = parseOr();
        while (match(Kind.COALESCE)) {
            Node right = parseOr();
            // `??` is the one construct that can tell "absent" from "zero", so it cannot fold
            // against a left operand that is a lookup.
            Op l = left.op();
            Op r = right.op();
            boolean leftAlwaysDefined = left.isConstant();
            left = leftAlwaysDefined
                    ? left
                    : Node.dynamic(ctx -> {
                        float value = l.apply(ctx);
                        return Float.isNaN(value) ? r.apply(ctx) : value;
                    });
        }
        return left;
    }

    private Node parseOr() {
        Node left = parseAnd();
        while (match(Kind.OR)) {
            left = binary(left, parseAnd(), null,
                    (a, b) -> truthy(a) || truthy(b) ? 1f : 0f);
        }
        return left;
    }

    private Node parseAnd() {
        Node left = parseEquality();
        while (match(Kind.AND)) {
            left = binary(left, parseEquality(), null,
                    (a, b) -> truthy(a) && truthy(b) ? 1f : 0f);
        }
        return left;
    }

    private Node parseEquality() {
        Node left = parseComparison();
        while (true) {
            if (match(Kind.EQ)) {
                left = binary(left, parseComparison(), null, (a, b) -> a == b ? 1f : 0f);
            } else if (match(Kind.NEQ)) {
                left = binary(left, parseComparison(), null, (a, b) -> a != b ? 1f : 0f);
            } else {
                return left;
            }
        }
    }

    private Node parseComparison() {
        Node left = parseAdditive();
        while (true) {
            if (match(Kind.LT)) {
                left = binary(left, parseAdditive(), null, (a, b) -> a < b ? 1f : 0f);
            } else if (match(Kind.LTE)) {
                left = binary(left, parseAdditive(), null, (a, b) -> a <= b ? 1f : 0f);
            } else if (match(Kind.GT)) {
                left = binary(left, parseAdditive(), null, (a, b) -> a > b ? 1f : 0f);
            } else if (match(Kind.GTE)) {
                left = binary(left, parseAdditive(), null, (a, b) -> a >= b ? 1f : 0f);
            } else {
                return left;
            }
        }
    }

    private Node parseAdditive() {
        Node left = parseMultiplicative();
        while (true) {
            if (match(Kind.PLUS)) {
                left = binary(left, parseMultiplicative(), null, Float::sum);
            } else if (match(Kind.MINUS)) {
                left = binary(left, parseMultiplicative(), null, (a, b) -> a - b);
            } else {
                return left;
            }
        }
    }

    private Node parseMultiplicative() {
        Node left = parseUnary();
        while (true) {
            if (match(Kind.STAR)) {
                left = binary(left, parseUnary(), null, (a, b) -> a * b);
            } else if (match(Kind.SLASH)) {
                // Division by zero yields 0 rather than an infinity. Bedrock's behaviour, and an
                // infinity that reached a bone matrix would put the model at no coordinate at all.
                left = binary(left, parseUnary(), null, (a, b) -> b == 0f ? 0f : a / b);
            } else {
                return left;
            }
        }
    }

    private Node parseUnary() {
        if (match(Kind.MINUS)) {
            Node operand = parseUnary();
            return operand.isConstant()
                    ? Node.of(-operand.constantOrZero())
                    : negate(operand.op());
        }
        if (match(Kind.NOT)) {
            Node operand = parseUnary();
            return operand.isConstant()
                    ? Node.of(truthy(operand.constantOrZero()) ? 0f : 1f)
                    : not(operand.op());
        }
        return parsePrimary();
    }

    private static Node negate(Op op) {
        return Node.dynamic(ctx -> -op.apply(ctx));
    }

    private static Node not(Op op) {
        return Node.dynamic(ctx -> truthy(op.apply(ctx)) ? 0f : 1f);
    }

    // ── Primaries ────────────────────────────────────────────────────────────────────────────

    private Node parsePrimary() {
        Token token = peek();
        switch (token.kind()) {
            case NUMBER -> {
                advance();
                return Node.of(token.number());
            }
            case STRING -> {
                advance();
                // Molang strings only ever meet == and !=. Interning them to a float id makes those
                // work; anything arithmetic on a string is meaningless in Bedrock too.
                return Node.of(MolangStrings.intern(token.text()));
            }
            case LPAREN -> {
                advance();
                Node inner = parseAssignment();
                expect(Kind.RPAREN, "expected ')'");
                return inner;
            }
            case IDENTIFIER -> {
                return parseAccess();
            }
            case LBRACE -> throw fail(token, "block expressions are not supported yet (SC-130 §2.4)");
            default -> throw fail(token, "expected a value, found '" + token.text() + "'");
        }
    }

    private Node parseAccess() {
        Token head = advance();
        String scopeName = head.text();
        expectDot(head);
        Token nameToken = expect(Kind.IDENTIFIER, "expected a name after '" + scopeName + ".'");
        String name = nameToken.text();

        List<Node> arguments = List.of();
        if (match(Kind.LPAREN)) {
            arguments = parseArguments();
        }
        if (check(Kind.ARROW)) {
            throw fail(peek(), "'->' dereference is not supported yet (SC-130 §2.5)");
        }

        if (scopeName.equals("math")) {
            return math(name, arguments, nameToken);
        }

        MolangContext.Scope scope = scopeOf(scopeName);
        if (scope == null) {
            unresolved.add(scopeName + "." + name);
            return Node.of(0f);
        }
        if (scope == MolangContext.Scope.QUERY) {
            queries.add(name);
        }
        if (arguments.isEmpty()) {
            return Node.dynamic(ctx -> ctx.read(scope, name));
        }
        List<Op> argOps = arguments.stream().map(Node::op).toList();
        int arity = argOps.size();
        return Node.dynamic(ctx -> {
            float[] values = new float[arity];
            for (int i = 0; i < arity; i++) {
                values[i] = argOps.get(i).apply(ctx);
            }
            return ctx.call(scope, name, values);
        });
    }

    private List<Node> parseArguments() {
        List<Node> out = new ArrayList<>();
        if (match(Kind.RPAREN)) {
            return out;
        }
        do {
            out.add(parseAssignment());
        } while (match(Kind.COMMA));
        expect(Kind.RPAREN, "expected ')' to close the argument list");
        return out;
    }

    /**
     * Binds a {@code math.*} name at compile time.
     *
     * <p>Pure functions, so a call with constant arguments folds away entirely. The random family is
     * the exception: it reads the context's seeded source and can never fold.
     */
    private Node math(String name, List<Node> arguments, Token where) {
        MolangMathBinding binding = MolangMathBinding.byName(name);
        if (binding == null) {
            unresolved.add("math." + name);
            return Node.of(0f);
        }
        if (arguments.size() != binding.arity()) {
            throw fail(where, "math." + name + " takes " + binding.arity()
                    + " argument(s), got " + arguments.size());
        }
        List<Op> ops = arguments.stream().map(Node::op).toList();
        Function<MolangContext, float[]> gather = ctx -> {
            float[] values = new float[ops.size()];
            for (int i = 0; i < ops.size(); i++) {
                values[i] = ops.get(i).apply(ctx);
            }
            return values;
        };
        if (binding.pure() && arguments.stream().allMatch(Node::isConstant)) {
            float[] constants = new float[arguments.size()];
            for (int i = 0; i < arguments.size(); i++) {
                constants[i] = arguments.get(i).constantOrZero();
            }
            return Node.of(binding.apply(MolangContext.standalone(), constants));
        }
        return Node.dynamic(ctx -> binding.apply(ctx, gather.apply(ctx)));
    }

    // ── Token plumbing ───────────────────────────────────────────────────────────────────────

    /** {@code q. v. t. c.} are the short forms; anything else is not a scope. */
    private static MolangContext.Scope scopeOf(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "query", "q" -> MolangContext.Scope.QUERY;
            case "variable", "v" -> MolangContext.Scope.VARIABLE;
            case "temp", "t" -> MolangContext.Scope.TEMP;
            case "context", "c" -> MolangContext.Scope.CONTEXT;
            default -> null;
        };
    }

    /** Bedrock treats any non-zero value as true, including negatives and fractions. */
    static boolean truthy(float value) {
        return value != 0f;
    }

    private Token peek() {
        return tokens.get(at);
    }

    private Token advance() {
        return tokens.get(at++);
    }

    private boolean check(Kind kind) {
        return peek().kind() == kind;
    }

    private boolean match(Kind kind) {
        if (check(kind)) {
            at++;
            return true;
        }
        return false;
    }

    private Token expect(Kind kind, String message) {
        if (!check(kind)) {
            throw fail(peek(), message);
        }
        return advance();
    }

    private void expectDot(Token head) {
        if (!match(Kind.DOT)) {
            throw fail(head, "'" + head.text() + "' is not a value; expected a scope such as "
                    + "query., variable., temp., context. or math.");
        }
    }

    private MolangSyntaxException fail(Token token, String message) {
        return new MolangSyntaxException(message, source, token.line(), token.column());
    }
}
