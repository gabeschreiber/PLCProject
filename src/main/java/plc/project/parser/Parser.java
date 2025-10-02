package plc.project.parser;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast parse(String rule) throws ParseException {
        var ast = switch (rule) {
            case "source" -> parseSource();
            case "stmt" -> parseStmt();
            case "expr" -> parseExpr();
            default -> throw new AssertionError(rule);
        };
        if (tokens.has(0)) {
            throw new ParseException("Expected end of input.", tokens.getNext());
        }
        return ast;
    }

    private Ast.Source parseSource() throws ParseException {
        var statements = new ArrayList<Ast.Stmt>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    private Ast.Stmt parseStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseLetStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseDefStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseIfStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseForStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseReturnStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    // expr ::= logical_expr
    private Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }

    // logical_expr ::= comparison_expr (('AND' | 'OR') comparison_expr)*
    private Ast.Expr parseLogicalExpr() throws ParseException {
        Ast.Expr left = parseComparisonExpr();
        while (tokens.match("AND") || tokens.peek("OR")) {
            String operator = tokens.get(-1).literal();
            Ast.Expr right = parseComparisonExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    // comparison_expr ::= additive_expr (('<' | '<=' | '>' | '>=' | '==' | '!=') additive_expr)*
    private Ast.Expr parseComparisonExpr() throws ParseException {
        Ast.Expr left = parseAdditiveExpr();

        while (tokens.match("<") || tokens.peek("<=") || tokens.peek(">") || tokens.peek(">=") || tokens.peek("==") || tokens.peek("!=")) {
            String operator = tokens.get(-1).literal();
            Ast.Expr right = parseAdditiveExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }

        return left;
    }

    // additive_expr ::= multiplicative_expr (('+' | '-') multiplicative_expr)*
    private Ast.Expr parseAdditiveExpr() throws ParseException {
        Ast.Expr left = parseMultiplicativeExpr();

        while (tokens.peek("+") || tokens.peek("-")) {
            String operator = tokens.get(0).literal();
            tokens.match(operator);
            Ast.Expr right = parseMultiplicativeExpr();
            left = new Ast.Expr.Binary(operator, left, right);
        }

        return left;
    }

    // multiplicative_expr ::= secondary_expr (('*' | '/') secondary_expr)*
    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        Ast.Expr left = parseSecondaryExpr();

        while (tokens.peek("*") || tokens.peek("/")) {
            String operator = tokens.get(0).literal();
            tokens.match(operator);
            Ast.Expr right = parseSecondaryExpr();
            left = new Ast.Expr.Binary(operator, left, right); // keep expanding on left while more operators exist
        }

        return left;
    }

    // secondary_expr ::= primary_expr property_or_method*
    private Ast.Expr parseSecondaryExpr() throws ParseException {
        var primaryExpr = parsePrimaryExpr();
        while (tokens.peek(".")) {
            primaryExpr = parsePropertyOrMethod(primaryExpr);
        }
        return primaryExpr;
    }

    // property_or_method ::= '.' identifier ('(' (expr (',' expr)*)? ')')?
    private Ast.Expr parsePropertyOrMethod(Ast.Expr receiver) throws ParseException {
        if (!tokens.match(".")) {
            throw new ParseException("Expected '.' in expression.", tokens.getNext());
        }

        if (!tokens.has(0)) {
            throw new ParseException("Expected identifier in expression.", tokens.getNext());
        }

        var token = tokens.get(0);

        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier in expression.", tokens.getNext());
        }

        String name = token.literal();

        if (tokens.match("(")) {
            List<Ast.Expr> args = new ArrayList<>();
            if (!tokens.peek(")")) {
                args.add(parseExpr());
                while (tokens.match(",")) {
                    args.add(parseExpr());
                }
            }

            if (!tokens.match(")")) {
                throw new ParseException("Missing closing parentheses in expression.", tokens.getNext());
            }

            return new Ast.Expr.Method(receiver, name, args);
        }

        return new Ast.Expr.Property(receiver, name);
    }

    // primary_expr ::= literal_expr | group_expr | object_expr | variable_or_function_expr
    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if (!tokens.has(0)) {
            throw new ParseException("Expected expression, found end of input.", tokens.getNext());
        }
        var token = tokens.get(0);
        List<Token.Type> types = List.of(Token.Type.INTEGER, Token.Type.DECIMAL, Token.Type.CHARACTER, Token.Type.STRING);

        if (types.contains(token.type()) || token.literal().equals("NIL") || token.literal().equals("TRUE") || token.literal().equals("FALSE")) {
            return parseLiteralExpr();
        } else if (token.literal().equals("(")) {
            return parseGroupExpr();
        } else if (token.literal().equals("OBJECT")) {
            return parseObjectExpr();
        } else if (token.type().equals(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        }
        throw new ParseException("No Literal, Group, Object, or Variable/Function Expression found", tokens.getNext());
    }

    // literal_expr ::= 'NIL' | 'TRUE' | 'FALSE' | integer | decimal | character | string
    private Ast.Expr parseLiteralExpr() throws ParseException {
        var token = tokens.get(0);
        tokens.match(token.type());
        if (token.type().equals(Token.Type.IDENTIFIER)) {
            if (token.literal().equals("NIL")) {
                return new Ast.Expr.Literal(null);
            } else if (token.literal().equals("TRUE")) {
                return new Ast.Expr.Literal(true); // or Boolean.TRUE
            } else if (token.literal().equals("FALSE")) {
                return new Ast.Expr.Literal(false);
            }
            return new Ast.Expr.Variable(token.literal());
        } else if (token.type().equals(Token.Type.INTEGER)) {
            if (token.literal().contains("e")) {
                return new Ast.Expr.Literal(new BigDecimal(token.literal()));
            } else {
                return new Ast.Expr.Literal(new BigInteger(token.literal()));
            }
        } else if (token.type().equals(Token.Type.DECIMAL)) {
            return new Ast.Expr.Literal(new BigDecimal(token.literal()));
        } else if (token.type().equals(Token.Type.CHARACTER)) {
            return new Ast.Expr.Literal(token.literal().charAt(1)); // RN this and string are removing the quotes ' or " wrapping the values, dont know if thats supposed to be the case or not
        } else if (token.type().equals(Token.Type.STRING)) {
            String literal = token.literal().substring(1, token.literal().length() - 1);
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < literal.length(); i++) {
                char c = literal.charAt(i);
                if (c == '\\' && i < literal.length() - 1) {
                    char next = literal.charAt(++i);
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '\\' -> sb.append('\\');
                        case '\'' -> sb.append('\'');
                        case '"' -> sb.append('"');
                        default -> sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
            }

            String newLiteral = sb.toString();

            return new Ast.Expr.Literal(newLiteral);
        }
        throw new ParseException("Expected a literal expression.", Optional.of(token)); // Might have to be token instead of tokens.getNext() since we already took and consumed it
    }

    // group_expr ::= '(' expr ')'
    private Ast.Expr parseGroupExpr() throws ParseException {
        if (!tokens.match("(")) {
            throw new ParseException("Missing opening parentheses in group expression.", tokens.getNext());
        }
        var expr = parseExpr();
        if (!tokens.match(")")) {
           throw new ParseException("Missing closing parentheses in group expression.", tokens.getNext()); // Check index here
        }
        return new Ast.Expr.Group(expr);
    }

    // object_expr ::= 'OBJECT' identifier? 'DO' let_stmt* def_stmt* 'END'
    private Ast.Expr parseObjectExpr() throws ParseException {
        // if (tokens.match("OBJECT")) { var token = tokens.get(-1);} I realized after you could just do this but want to see if my original works
        var token = tokens.get(0);

        String name = "";

        if (!token.literal().equals("OBJECT")) {
            throw new ParseException("Expected a object expression.", Optional.of(token));
        } else {
            tokens.match(token.type());
        }

        if (tokens.peek("DO")) {
            tokens.match("DO");
            name = tokens.get(0).literal();
        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            name = tokens.get(0).literal();
            tokens.match(Token.Type.IDENTIFIER);
            if (!tokens.match("DO")) {
                throw new ParseException("Missing 'DO' after identifier", tokens.getNext());
            }
        } else {
            throw new ParseException("Missing 'DO' in Object expression", tokens.getNext());
        }

        List<Ast.Stmt.Let> let_stmts = new ArrayList<>();
        while (tokens.get(0).literal().equals("LET")) {
            // tokens.match("LET"); we will consume it in parseLetStmt()
            let_stmts.add((Ast.Stmt.Let) parseLetStmt());
        }

        List<Ast.Stmt.Def> def_stmts = new ArrayList<>();
        while (tokens.get(0).literal().equals("DEF")) {
            // tokens.match("LET"); we will consume it in parseLetStmt()
            def_stmts.add((Ast.Stmt.Def) parseDefStmt());
        }

        if (!tokens.match("END")) {
            throw new ParseException("Missing 'END' in Object expression", tokens.getNext());
        }

        return new Ast.Expr.ObjectExpr(Optional.ofNullable(name), let_stmts, def_stmts);
    }

    // variable_or_function_expr ::= identifier ('(' (expr (',' expr)*)? ')')?
    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        var token = tokens.get(0);
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier in expression.", tokens.getNext());
        }

        String identifier = token.literal();

        if (tokens.match("(")) {
            List<Ast.Expr> args = new ArrayList<>();
            if (!tokens.peek(")")) {
                args.add(parseExpr());
                while (tokens.match(",")) {
                    args.add(parseExpr());
                }
            }

            if (!tokens.match(")")) {
                throw new ParseException("Missing closing parentheses in expression.", tokens.getNext());
            }

            return new Ast.Expr.Function(identifier, args);
        }

        return new Ast.Expr.Variable(identifier);
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            Preconditions.checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns the next token, if present.
         */
        public Optional<Token> getNext() {
            return index < tokens.size() ? Optional.of(tokens.get(index)) : Optional.empty();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                Preconditions.checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
