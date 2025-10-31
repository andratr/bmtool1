package org.learningjava.bmtool1.infrastructure.adapter.out.blockASTParser;

import java9.Java9Lexer;
import java9.Java9Parser;
import java9.Java9ParserBaseListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.learningjava.bmtool1.application.port.BlockExtractorPort;
import org.learningjava.bmtool1.domain.model.pairs.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component("javaBlockExtractor")
public class JavaBlockExtractorAdapter implements BlockExtractorPort {

    private static final Logger log = LoggerFactory.getLogger(JavaBlockExtractorAdapter.class);

    @Override
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
                String name = ctx.methodHeader().methodDeclarator().identifier().getText();
                int start = ctx.getStart().getStartIndex();
                int stop = ctx.getStop().getStopIndex();
                Interval interval = new Interval(start, stop);
                String methodCode = ctx.start.getInputStream().getText(interval);

                blocks.add(new Block("METHOD", methodCode, javaFile.toString()));
                log.debug("Recognized method: {} (length={})", name, methodCode.length());
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
