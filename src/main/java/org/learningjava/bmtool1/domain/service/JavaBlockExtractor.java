// src/main/java/org/learningjava/bmtool1/domain/service/JavaBlockExtractor.java
package org.learningjava.bmtool1.domain.service;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.*;
import org.learningjava.bmtool1.domain.model.Block;
import java9.Java9Lexer;
import java9.Java9Parser;
import java9.Java9ParserBaseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaBlockExtractor {
    private static final Logger log = LoggerFactory.getLogger(JavaBlockExtractor.class);

    public List<Block> extract(Path javaFile) throws Exception {
        log.info("Extracting Java blocks from {}", javaFile);

        String content = Files.readString(javaFile);
        Java9Lexer lexer = new Java9Lexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Java9Parser parser = new Java9Parser(tokens);

        ParseTree tree = parser.compilationUnit();
        List<Block> blocks = new ArrayList<>();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new Java9ParserBaseListener() {
            @Override
            public void enterMethodDeclaration(Java9Parser.MethodDeclarationContext ctx) {
                // short method name (identifier only)
                String name = ctx.methodHeader().methodDeclarator().identifier().getText();

                // full method text with formatting from the original file
                int start = ctx.getStart().getStartIndex();
                int stop = ctx.getStop().getStopIndex();
                Interval interval = new Interval(start, stop);
                String methodCode = ctx.start.getInputStream().getText(interval);

                // add Block with the full snippet
                blocks.add(new Block("METHOD", methodCode, javaFile.toString()));

                log.debug("Recognized method: {} -> snippet length={}", name, methodCode.length());
            }

            @Override
            public void enterFieldDeclaration(Java9Parser.FieldDeclarationContext ctx) {
                int start = ctx.getStart().getStartIndex();
                int stop = ctx.getStop().getStopIndex();
                Interval interval = new Interval(start, stop);
                String fieldCode = ctx.start.getInputStream().getText(interval);

                blocks.add(new Block("FIELD", fieldCode, javaFile.toString()));

                log.debug("Recognized field: {}", fieldCode);
            }



            @Override
            public void enterStatement(Java9Parser.StatementContext ctx) {
                blocks.add(new Block("STATEMENT", ctx.getText(), javaFile.toString()));
                log.trace("Recognized statement: {}", ctx.getText());
            }
        }, tree);

        log.info("Extracted {} Java blocks from {}", blocks.size(), javaFile);
        return blocks;
    }
}
