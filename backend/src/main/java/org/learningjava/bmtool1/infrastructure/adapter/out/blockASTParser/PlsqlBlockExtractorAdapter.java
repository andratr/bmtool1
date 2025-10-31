package org.learningjava.bmtool1.infrastructure.adapter.out.blockASTParser;

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
import plsql.PlSqlLexer;
import plsql.PlSqlParser;
import plsql.PlSqlParserBaseListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component("plsqlBlockExtractor")
public class PlsqlBlockExtractorAdapter implements BlockExtractorPort {

    private static final Logger log = LoggerFactory.getLogger(PlsqlBlockExtractorAdapter.class);

    @Override
    public List<Block> extract(Path plsqlFile) throws Exception {
        log.info("Extracting PL/SQL blocks from {}", plsqlFile);

        String content = Files.readString(plsqlFile);
        PlSqlLexer lexer = new PlSqlLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlSqlParser parser = new PlSqlParser(tokens);

        ParseTree tree = parser.sql_script();
        List<Block> blocks = new ArrayList<>();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new PlSqlParserBaseListener() {

            @Override
            public void enterAssignment_statement(PlSqlParser.Assignment_statementContext ctx) {
                String text = ctx.getText();
                if (text.contains("'")) {
                    blocks.add(new Block("ASSIGNMENT_CONST_STRING", text, plsqlFile.toString()));
                    log.debug("Recognized assignment: {}", text);
                }
            }

            @Override
            public void enterIf_statement(PlSqlParser.If_statementContext ctx) {
                blocks.add(new Block("CONDITION", ctx.getText(), plsqlFile.toString()));
                log.debug("Recognized IF condition: {}", ctx.getText());
            }

            @Override
            public void enterInsert_statement(PlSqlParser.Insert_statementContext ctx) {
                String sql = ctx.start.getInputStream()
                        .getText(new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex()));
                blocks.add(new Block("INSERT_STATEMENT", sql, plsqlFile.toString()));
                log.debug("Recognized INSERT block with preserved whitespace.");
            }

            @Override
            public void enterException_handler(PlSqlParser.Exception_handlerContext ctx) {
                blocks.add(new Block("EXCEPTION_HANDLER", ctx.getText(), plsqlFile.toString()));
                log.debug("Recognized exception handler: {}", ctx.getText());
            }
        }, tree);

        log.info("Extracted {} PL/SQL blocks from {}", blocks.size(), plsqlFile);
        return blocks;
    }
}
