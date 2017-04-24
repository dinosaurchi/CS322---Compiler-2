import java.io.*;
import java.util.*;

// **********************************************************************
// Ast class (base class for all other kinds of nodes)
// **********************************************************************
abstract class Ast {
    protected SymbolTable table;
    protected CodeBuffer code = new CodeBuffer();
}

enum Tag {
    GLOBAL,
    LOCAL,
    PARAM,
    CALL,
    CALLF
}

class SymbolTable {
    private SymbolTable ancestor;
	public List<SymbolTable> children;
    public String currentFn;
	
	private Map<String,String> localVar, localParam;
	private int localCnt, paramCnt, tempCnt;
	
	private static Map<String,String> globalVar, stringVar;
	private static CodeBuffer codeString;
	private static int globalCnt, stringCnt, funcCnt, labelCnt;
	
	public static void initGlobal(){
		globalCnt = stringCnt = funcCnt = labelCnt = 0;
		codeString = new CodeBuffer();
		globalVar = new HashMap<String, String> ();
		stringVar = new HashMap<String, String> ();
	}
	
    public SymbolTable() {
		localCnt = paramCnt = tempCnt = 0;
		localVar = new HashMap<String, String>();
		localParam = new HashMap<String, String>();
		children = new ArrayList<SymbolTable>();
		ancestor = null;
    }

    public SymbolTable(SymbolTable ancestor) {
		this();
		currentFn = ancestor.currentFn;
		localCnt = ancestor.localCnt;
		paramCnt = ancestor.paramCnt;
		tempCnt = ancestor.tempCnt;
        this.ancestor = ancestor;
		ancestor.children.add(this);
    }

    public void enterVariable(Id name, Type type, Tag tag) {
		if(tag == Tag.LOCAL){
			localVar.put(name.lexeme(), "@"+String.valueOf(localCnt++));
		}else if(tag == Tag.PARAM){
			localParam.put(name.lexeme(), "%"+String.valueOf(paramCnt++));
		}else if(tag == Tag.GLOBAL){
			SymbolTable.globalVar.put(name.lexeme(), "$"+String.valueOf(SymbolTable.globalCnt++));
		}
    }

    public void enterFunction(Id name, Type type, FormalsList formalList) {
        // Do nothing
    }

    public String enterString(String s) {
		if(stringVar.containsKey(s)) 
            return stringVar.get(s);
        String res = "?" + (SymbolTable.stringCnt++);
		SymbolTable.codeString.append("str " + s);
		stringVar.put(s,res);
		return res;
    }

    public int countGlobal() {
        return SymbolTable.globalCnt;
    }

    public int countLocal() {
        return countLocalRecursive();
    }
	
	private int countLocalRecursive(){
		int res = localCnt;
		for(SymbolTable table : children){
			int t = table.countLocalRecursive();
			if(res < t) res = t;
		}
		return res;
	}

    public int countTemp() {
        return countTempRecursive();
    }
	
	private int countTempRecursive(){
		int res = tempCnt;
		for(SymbolTable table : children){
			int t = table.countTempRecursive();
			if(res < t) res = t;
		}
		return res;
	}

    public String newLabel() {
        return "~" + (SymbolTable.labelCnt++);
    }
	
	public String newTemp(){
		return "&" + (tempCnt++);
	}

    public String lookup(Id name){
        SymbolTable cur = this;
		while(cur!=null){
			if(cur.localVar.containsKey(name.lexeme()))
				return cur.localVar.get(name.lexeme());
			if(cur.localParam.containsKey(name.lexeme()))
				return cur.localParam.get(name.lexeme());
			
			cur = cur.ancestor;
		}
		
		if(SymbolTable.globalVar.containsKey(name.lexeme()))
			return SymbolTable.globalVar.get(name.lexeme());
		
		return null;
    }

    public CodeBuffer getConstantCode() {
        return SymbolTable.codeString;
    }
}

class CodeBuffer {
    private List<String> list;

    public CodeBuffer() {
        list = new ArrayList<String>();
    }

    public void append(String s) {
        list.add(s);
    }

    public void append(CodeBuffer o) {
        for (String s: o.list)
            list.add(s);
    }
	
	public void appendTab(CodeBuffer o) {
        if (o == null) return;
        for (String s: o.list)
            list.add("    " + s);
    }

    public void appendLn() {
        append("");
    }

    public void output(PrintWriter out) {
        for (String s: list)
            out.println(s);
    }
}

class Program extends Ast {
    private DeclList declList;

    public Program(DeclList declList) {
        this.declList = declList;
    }

    // Compile
    public void compile(PrintWriter out) {
        table = new SymbolTable();
		table.initGlobal();
        declList.table = table;
        declList.compile(Tag.GLOBAL);

        code = new CodeBuffer();
        code.append(table.getConstantCode());
		code.appendLn();
        code.append(String.format("entry main, %d", table.countGlobal()));
        code.append(declList.code);

        code.output(out);
    }
}

// **********************************************************************
// Decls
// **********************************************************************
class DeclList extends Ast {
    // linked list of kids (Decls)
    protected LinkedList decls;

    public DeclList(LinkedList decls) {
        this.decls = decls;
    }

    public void compile(Tag tag) {
        code = new CodeBuffer();
        ListIterator listIterator = decls.listIterator();
        while (listIterator.hasNext()) {
            Decl decl = (Decl) listIterator.next();
            decl.table = table;
            decl.compile(tag);
			code.appendLn();
            code.append(decl.code);
        }
    }
}

abstract class Decl extends Ast {
    public abstract void compile(Tag tag);
}

class VarDecl extends Decl {
    private Type type;
    private Id name;

    public VarDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public void compile(Tag tag) {
        table.enterVariable(name, type, tag);
    }
}

class FnDecl extends Decl {
    private Type type;
    private Id name;
    private FormalsList formalList;
    private FnBody body;

    public FnDecl(Type type, Id name, FormalsList formalList, FnBody body) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
        this.body = body;
    }

    @Override
    public void compile(Tag tag) {
        table.enterFunction(name, type, formalList);

        // We copy a new Symbol table, because with another Function declaration, we can have ...
        //... a local variable with the same type and name. Thus if just use the common symbol table,...
        //... we will encounter the duplication of usage for these variables. 
        formalList.table = new SymbolTable(table);
        formalList.compile();

        // The function body need the reference from the parameters list,...
        // In this case we use the common symbol table because the function body contains...
        //...only the declList and Statement list. and the statement List wont be divided into isolate....
        //...usage (for allowing redefination, only fir HIR), thus we dont care about the case as the formalList.
        body.table = formalList.table;
        body.table.currentFn = name.lexeme();
        body.compile();
		
        code = new CodeBuffer();
        code.append("func " + name.lexeme());
        code.append(String.format("funci %d, %d", body.table.countLocal(), body.table.countTemp()));
        code.appendTab(body.code);
        code.append("efunc " + name.lexeme());
    }
}

class FnPreDecl extends Decl {
    private Type type;
    private Id name;
    private FormalsList formalList;

    public FnPreDecl(Type type, Id name, FormalsList formalList) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
    }

    @Override
    public void compile(Tag tag) {
        // We dont need function pre-decl, thus nothing will be compiled
        // Do nothing
    }
}

class FormalsList extends Ast {
    // linked list of kids (FormalDecls)
    private LinkedList formals;

    public FormalsList(LinkedList formals) {
        this.formals = formals;
    }

    public void compile() {
        code = new CodeBuffer();
        ListIterator listIterator = formals.listIterator();
        while (listIterator.hasNext()) {
            FormalDecl decl = (FormalDecl) listIterator.next();
            decl.table = table;

            // Each parameter declaration will be added to the symbol table with tag Parameter
            decl.compile(Tag.PARAM);
            code.append(decl.code);
        }
    }
}

class FormalDecl extends Decl {
    private Type type;
    private Id name;

    public FormalDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public void compile(Tag tag) {
        table.enterVariable(name, type, tag);
    }
}

class FnBody extends Ast {
    private DeclList declList;
    private StmtList stmtList;

    public FnBody(DeclList declList, StmtList stmtList) {
        this.declList = declList;
        this.stmtList = stmtList;
    }

    public void compile() {
        // Each declList AST needs the reference from current function body declaration information
        declList.table = table; 

        // In this case, there is no function being declared within another function, thus..
        //... declList in this case is only a list of local varDecl
        declList.compile(Tag.LOCAL);

        // The statement list needs information from variable declaration and parameter declaration within this function
        // The statement list also needs global information
        stmtList.table = table; // this "table" appended information from higher level of node in AST 
		
        // We prepare the next position for the last statement in the statement list
        stmtList.nextLabel = table.newLabel();
        stmtList.compile();

        // The code of the function body is simply the code of the statements within the function...
        //...because declList only add variables into the Symbol table for supporting the statement list compiling process. 
        code.append(stmtList.code);
        // Because at the end of each function call, it always returns back to where it was called,...
        //...thus we do not need to assign a label at the end of the statement list 
        //code.append(stmtList.nextLabel + ":");
    }
}

class StmtList extends Ast {
    // linked list of kids (Stmts)
    private LinkedList stmts;
	public String nextLabel;
	
    public StmtList(LinkedList stmts) {
        this.stmts = stmts;
    }

    public void compile() {
        code = new CodeBuffer();
        ListIterator listIterator = stmts.listIterator();
        while (listIterator.hasNext()) {
            Stmt stmt = (Stmt) listIterator.next();
            stmt.table = table;

            if (listIterator.hasNext()) 
                stmt.nextLabel = table.newLabel();
			else 
                // The next position of the last statement must be the same with...
                //...the next position of the whole statement list of function.
                stmt.nextLabel = nextLabel;
            stmt.compile();

            code.append(stmt.code);

            // Because at the end of each function call, it always returns back to where it was called,...
            //...thus we do not need to assign a label at the end of the statement list 
            if (listIterator.hasNext()) 
                code.append(stmt.nextLabel + ":");
        }
    }
}

// **********************************************************************
// Types
// **********************************************************************
class Type extends Ast {
    public static final String voidTypeName = "void";
    public static final String intTypeName = "int";

    private String name;
    private int size;  // use if this is an array type
    private int numPointers;

    private Type() {
    }
    
    public static Type CreateSimpleType(String name) {
        Type t = new Type();
        t.name = name;
        t.size = -1;
        t.numPointers = 0;
        
        return t;
    }

    public static Type CreateArrayType(String name, int size) {
        Type t = new Type();
        t.name = name;
        t.size = size;
        t.numPointers = 0;
        
        return t;
    }

    public static Type CreatePointerType(String name, int numPointers) {
        Type t = new Type();
        t.name = name;
        t.size = -1;
        t.numPointers = numPointers;
        
        return t;
    }

    public static Type CreateArrayPointerType(String name, int size, int numPointers) {
        Type t = new Type();
        t.name = name;
        t.size = size;
        t.numPointers = numPointers;
        
        return t;
    }
    
    public String name() {
        return name;
    }
}

// **********************************************************************
// Stmts
// **********************************************************************
abstract class Stmt extends Ast {
    public abstract void compile();
    public String nextLabel;
}

class AssignStmt extends Stmt {
    private Exp lhs;
    private Exp exp;

    public AssignStmt(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }

    @Override
    public void compile() {
        exp.table = table;
        exp.compile();

        lhs.table = table;
        lhs.compile();

        code = new CodeBuffer();
        code.append(exp.code);
        code.append(String.format("move %s, %s", lhs.addr, exp.addr));
    }
}

class IfStmt extends Stmt {
    private Exp exp;
    private DeclList declList;
    private StmtList stmtList;

    public IfStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    @Override
    public void compile() {
        exp.trueLabel = table.newLabel();
        exp.falseLabel = nextLabel;
        exp.table = table;
        exp.compile();

        declList.table = new SymbolTable(table);
        declList.compile(Tag.LOCAL);

        // The next position of the last statement must be the same with...
        //...the next position of the whole if statement.
        stmtList.table = declList.table;
		stmtList.nextLabel = nextLabel;
        stmtList.compile();

        code = new CodeBuffer();
        code.append(exp.code);
        code.append(exp.trueLabel + ":");
        code.append(stmtList.code);
        // if( B ) S1 

        // Because at the end of each function call, it always returns back to where it was called,...
        //...thus we do not need to assign a label at the end of the statement list 
    }
}

class IfElseStmt extends Stmt {
    private Exp exp;
    private DeclList declList1;
    private DeclList declList2;
    private StmtList stmtList1;
    private StmtList stmtList2;

    public IfElseStmt(Exp exp, DeclList declList1, StmtList stmtList1, 
            DeclList declList2, StmtList stmtList2) {
        this.exp = exp;
        this.declList1 = declList1;
        this.stmtList1 = stmtList1;
        this.declList2 = declList2;
        this.stmtList2 = stmtList2;
    }

    @Override
    public void compile() {
        exp.trueLabel = table.newLabel();
        exp.falseLabel = table.newLabel();
        exp.table = table;
        exp.compile();

        declList1.table = new SymbolTable(table);
        declList1.compile(Tag.LOCAL);

        stmtList1.table = declList1.table;
		stmtList1.nextLabel = nextLabel;
        stmtList1.compile();

        declList2.table = new SymbolTable(table);
        declList2.compile(Tag.LOCAL);

        stmtList2.table = declList2.table;
		stmtList2.nextLabel = nextLabel;
        stmtList2.compile();

        code = new CodeBuffer();
        code.append(exp.code);
        code.append(exp.trueLabel + ":");
        code.append(stmtList1.code);
        code.append("jump " + nextLabel);
        code.append(exp.falseLabel + ":");
        code.append(stmtList2.code);
        //if ( B ) S1 else S2
    }
}

class WhileStmt extends Stmt {
    private Exp exp;
    private DeclList declList1;
    private StmtList stmtList;

    public WhileStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList1 = declList1;
        this.stmtList = stmtList;
    }

    @Override
    public void compile() {
        String begin = table.newLabel();
        exp.trueLabel = table.newLabel();
        exp.falseLabel = nextLabel;
        exp.table = table;
        exp.compile();

        declList1.table = new SymbolTable(table);
        declList1.compile(Tag.LOCAL);

        stmtList.table = declList1.table;
		stmtList.nextLabel = begin;
        stmtList.compile();

        code = new CodeBuffer();
        code.append(begin + ":");
        code.append(exp.code);
        code.append(exp.trueLabel + ":");
        code.append(stmtList.code);
        code.append("jump " + begin);
    }
}

class ForStmt extends Stmt {
    private Stmt init;
    private Exp cond;
    private Stmt incr;
    private DeclList declList;
    private StmtList stmtList;

    public ForStmt(Stmt init, Exp cond, Stmt incr, 
            DeclList declList, StmtList stmtList) {
        this.init = init;
        this.cond = cond;
        this.incr = incr;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    @Override
    public void compile() {
        String begin = table.newLabel();

        init.nextLabel = begin;
        init.table = table;
        init.compile();

        cond.trueLabel = table.newLabel();
        cond.falseLabel = nextLabel;
        cond.table = table;
        cond.compile();

        declList.table = new SymbolTable(table);
        declList.compile(Tag.LOCAL);

        stmtList.table = declList.table;
		stmtList.nextLabel = table.newLabel();
        stmtList.compile();
        
        incr.nextLabel = begin;
        incr.table = table;
        incr.compile();

        code = new CodeBuffer();
        code.append(init.code);
        code.append(begin + ":");
        code.append(cond.code);
        code.append(cond.trueLabel + ":");
        code.append(stmtList.code);
        code.append(stmtList.nextLabel + ":");
        code.append(incr.code);
        code.append("jump " + begin);
    }
}

class CallStmt extends Stmt {
    private CallExp callExp;

    public CallStmt(CallExp callExp) {
        this.callExp = callExp;
    }

    @Override
    public void compile() {
        callExp.table = table;
        callExp.compile(Tag.CALL);
        code = callExp.code;
    }
}

class ReturnStmt extends Stmt {
    private Exp exp; // null for empty return

    public ReturnStmt(Exp exp) {
        this.exp = exp;
    }

    @Override
    public void compile() {
        code = new CodeBuffer();
        if (exp == null || exp.code == null) {
            code.append("ret " + table.currentFn);
        } else {
            exp.table = table;
            exp.compile();
            code.append(exp.code);
            code.append(String.format("retf %s, %s", table.currentFn, exp.addr));
        }
    }
}

// **********************************************************************
// Exps
// **********************************************************************
abstract class Exp extends Ast {
    public abstract int getLine();
    public abstract int getChar();
    
    public String addr;
    public String trueLabel;
    public String falseLabel;
    public abstract void compile();
}

abstract class BasicExp extends Exp {
    private int lineNum;
    private int charNum;
    
    public BasicExp(int lineNum, int charNum) {
        this.lineNum = lineNum;
        this.charNum = charNum;
    }
    
    public int getLine() {
        return lineNum;
    }
    public int getChar() {
        return charNum;
    }
}

class IntLit extends BasicExp {
    private int intVal;

    public IntLit(int lineNum, int charNum, int intVal) {
        super(lineNum, charNum);
        this.intVal = intVal;
    }

    @Override
    public void compile() {
        addr = String.valueOf(intVal);
    }
}

class StringLit extends BasicExp {
    private String strVal;

    public StringLit(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }

    public String str() {
        return strVal;
    }

    @Override
    public void compile() {
        addr = table.enterString(strVal);
    }
}

class Id extends BasicExp {
    private String strVal;

    public Id(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }

    public String lexeme() {
        return strVal;
    }

    @Override
    public void compile() {
        addr = table.lookup(this);
    }
}

class ArrayExp extends Exp {
    private Exp lhs;
    private Exp exp;

    public ArrayExp(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }

    public int getLine() {
        return lhs.getLine();
    }

    public int getChar() {
        return lhs.getChar();
    }

    @Override
    public void compile() {
        // Do nothing
    }
}

class CallExp extends Exp {
    private Id name;
    private ActualList actualList;

    public CallExp(Id name, ActualList actualList) {
        this.name = name;
        this.actualList = actualList;
    }

    public CallExp(Id name) {
        this.name = name;
        this.actualList = new ActualList(new LinkedList());
    }

    public int getLine() {
        return name.getLine();
    }

    public int getChar() {
        return name.getChar();
    }

    @Override
    public void compile() {
		code = new CodeBuffer();
		actualList.table = table;
        actualList.compile();
		addr = table.newTemp();
		code.append(actualList.code);
        code.append(String.format("callf %s, %s, %d", addr, name.lexeme(), actualList.size()));
    }

    public void compile(Tag tag) {
        actualList.table = table;
        actualList.compile();

        code = new CodeBuffer();
		
		//Custom for read and write
		if(name.lexeme().equals("print") || name.lexeme().equals("printf")){
			code.append("write " + actualList.getFirstExp());
			return;
		}
		
		if(name.lexeme().equals("scan") || name.lexeme().equals("scanf")){
			code.append("read " + actualList.getFirstExp());
			return;
		}
		
		// Normal
        code.append(actualList.code);
        if (tag == Tag.CALL) {
            code.append(String.format("call %s, %d", name.lexeme(), actualList.size()));
        }
        else {
            addr = table.newTemp();
            code.append(String.format("callf %s, %s, %d", addr, name.lexeme(), actualList.size()));
        }
    }
}

class ActualList extends Ast {
    // linked list of kids (Exps)
    private LinkedList exps;

    public ActualList(LinkedList exps) {
        this.exps = exps;
    }

    public int size() {
        return exps.size();
    }

	public String getFirstExp(){
		ListIterator listIterator = exps.listIterator();
		if(listIterator.hasNext()){
			Exp exp = (Exp) listIterator.next();
			return exp.addr;
		}
		return null;
	}
	
    public void compile() {
        code = new CodeBuffer();
        
        ListIterator listIterator = exps.listIterator();
        while (listIterator.hasNext()) {
            Exp exp = (Exp) listIterator.next();
            exp.table = table;
            exp.compile();
            code.append(exp.code);
        }
		int order = 0;
		listIterator = exps.listIterator();
        while (listIterator.hasNext()) {
            Exp exp = (Exp) listIterator.next();
            code.append(String.format("arg %s, %d", exp.addr, order++));
        }
    }
}

abstract class UnaryExp extends Exp {
    protected Exp exp;

    public UnaryExp(Exp exp) {
        this.exp = exp;
    }

    public int getLine() {
        return exp.getLine();
    }

    public int getChar() {
        return exp.getChar();
    }
}

abstract class BinaryExp extends Exp {
    protected Exp exp1;
    protected Exp exp2;

    public BinaryExp(Exp exp1, Exp exp2) {
        this.exp1 = exp1;
        this.exp2 = exp2;
    }

    public int getLine() {
        return exp1.getLine();
    }

    public int getChar() {
        return exp1.getChar();
    }

    @Override
    public void compile() {
        exp1.table = table;
        exp1.compile();

        exp2.table = table;
        exp2.compile();

        
        code = new CodeBuffer();
        code.append(exp1.code);
        code.append(exp2.code);
		
		addr = table.newTemp();
		finalStep();
    }

    protected abstract void finalStep();
}

abstract class BooleanExpr extends BinaryExp {
    public BooleanExpr(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    protected boolean isBooleanExpr() {
        return trueLabel != null && falseLabel != null;
    }

    @Override
    public void compile() {
        exp1.table = table;
        exp1.compile();

        exp2.table = table;
        exp2.compile();

        code = new CodeBuffer();
        combineCode();

        if (!isBooleanExpr()) {
            addr = table.newTemp();
            finalStep();
        }
    }

    protected void combineCode() {
        code.append(exp1.code);
        code.append(exp2.code);

        if (isBooleanExpr()) {
            addr = table.newTemp();
            finalStep();
            code.append(String.format("jt %s, %s", addr, trueLabel));
            code.append(String.format("jump %s", falseLabel));
        }
    }
}

// **********************************************************************
// UnaryExps
// **********************************************************************
class UnaryMinusExp extends UnaryExp {
    public UnaryMinusExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        exp.table = table;
        exp.compile();

        addr = table.newTemp();
        code = new CodeBuffer();
        code.append(exp.code);
        code.append(String.format("sub %s, 0, %s", addr, exp.addr));
    }
}

class NotExp extends UnaryExp {
    public NotExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        exp.table = table;
        exp.compile();

        addr = table.newTemp();
        code = new CodeBuffer();
        code.append(exp.code);
        code.append(String.format("not %s, %s", addr, exp.addr));

        if (trueLabel != null && falseLabel != null) {
            exp.trueLabel = falseLabel;
            exp.falseLabel = trueLabel;
        }
    }
}
class AddrOfExp extends UnaryExp {
    public AddrOfExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        // Do nothing
    }
}

class DeRefExp extends UnaryExp {
    public DeRefExp(Exp exp) {
        super(exp);
    }

    @Override
    public void compile() {
        // Do nothing
    }
}

// **********************************************************************
// BinaryExps
// **********************************************************************
class PlusExp extends BinaryExp {
    public PlusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("add %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class MinusExp extends BinaryExp {
    public MinusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("sub %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class TimesExp extends BinaryExp {
    public TimesExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("mult %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class DivideExp extends BinaryExp {
    public DivideExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("div %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class ModuloExp extends BinaryExp {
    public ModuloExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("mod %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class AndExp extends BooleanExpr {
    public AndExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("add %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class OrExp extends BooleanExpr {
    public OrExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("or %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class EqualsExp extends BooleanExpr {
    public EqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("eq %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class NotEqualsExp extends BooleanExpr {
    public NotEqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("neq %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class LessExp extends BooleanExpr {
    public LessExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("lt %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class GreaterExp extends BooleanExpr {
    public GreaterExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("gt %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class LessEqExp extends BooleanExpr {
    public LessEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("lte %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}

class GreaterEqExp extends BooleanExpr {
    public GreaterEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }

    @Override
    public void finalStep() {
        code.append(String.format("gte %s, %s, %s", addr, exp1.addr, exp2.addr));
    }
}