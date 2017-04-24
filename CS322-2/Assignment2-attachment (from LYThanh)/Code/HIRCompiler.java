import java.io.*;
import java_cup.runtime.*;

public class HIRCompiler {
    public static void main(String[] args) throws IOException {
        // SimpleC file
        String fileSimpleC = "";
        if (args.length >= 1) {
            fileSimpleC = args[0];
        } else {
            System.err.println("usage: HIRCompiler <SimpleC_file> <HIR_File>");
            System.exit(-1);
        }

        // HIR file
        String fileHIR = "";
        if (args.length >= 2) {
            fileHIR = args[1];
        } else {
            System.err.println("usage: HIRCompiler <SimpleC_file> <HIR_File>");
            System.exit(-1);
        }

        // Open input file
        FileReader reader = null;
        try {
            reader = new FileReader(fileSimpleC);
        } catch (FileNotFoundException ex) {
            System.err.println("File " + fileSimpleC + " not found!");
            System.exit(-1);
        }

        parser P = new parser(new Yylex(reader));

        Program program = null;
        try {
            program = (Program) P.parse().value;
        } catch (Exception ex) {
            System.err.println("Exception occured during parse: " + ex);
            System.exit(-1);
        }

        if (Errors.fatalError) {
            System.err.println("Confused by earlier errors: aborting");
            System.exit(0);
        }

        // Open output file
        PrintWriter writer = new PrintWriter(fileHIR);

        // Compile
        program.compile(writer);

        // Close and save files
        reader.close();
        writer.flush();
        writer.close();

        System.out.println("Finished!");
    }
}