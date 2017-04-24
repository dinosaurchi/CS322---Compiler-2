import java.io.*;
import java.util.*;

// **********************************************************************
// Ast class (base class for all other kinds of nodes)
// **********************************************************************
abstract class Ast {
    protected SymbolTable table;
}

class SymbolTable {
    public SymbolTable ancestor;
    public Type scopeFn;

    public Map<String, Type> mapVar;
    public Map<String, LinkedList> mapFn;

    static class Record {
        public Type type;
        public FormalsList list;

        public Record(Type type, FormalsList list) {
            this.type = type;
            this.list = list;
        }

        public boolean agree(FormalsList o) {
            if (o == null) return false;
            ListIterator it1 = list.formals.listIterator();
            ListIterator it2 = o.formals.listIterator();
            while (it1.hasNext() && it2.hasNext()) {
                FormalDecl d1 = (FormalDecl) it1.next();
                FormalDecl d2 = (FormalDecl) it2.next();
                if (!d1.type.agree(d2.type)) return false;
            }
            return it1.hasNext() == it2.hasNext();
        }

        public boolean agree(ActualList o) {
            if (o == null) return false;
            ListIterator it1 = list.formals.listIterator();
            ListIterator it2 = o.exps.listIterator();
            while (it1.hasNext() && it2.hasNext()) {
                FormalDecl d = (FormalDecl) it1.next();
                Exp e = (Exp) it2.next();
                if (!d.type.agree(e.type)) return false;
            }
            return it1.hasNext() == it2.hasNext();
        }
    }

    public SymbolTable() {
        mapVar = new TreeMap<String, Type>();
        mapFn = new TreeMap<String, LinkedList>();
    }

    public SymbolTable(SymbolTable ancestor) {
        this();
        this.ancestor = ancestor;
        this.scopeFn = ancestor.scopeFn;
        this.mapFn = ancestor.mapFn;
    }

    public boolean checkVar(Id id) {
        if (mapFn.containsKey(id.lexeme())) return false;
        return !mapVar.containsKey(id.lexeme());
    }

    public void enterVar(Id id, Type type) {
        mapVar.put(id.lexeme(), type);
    }

    public Type getTypeVar(Id id) {
        for (SymbolTable t = this; t != null; t = t.ancestor)
            if (t.mapVar.containsKey(id.lexeme())) return t.mapVar.get(id.lexeme());
        return null;
    }

    public boolean checkFn(Id id, FormalsList list) {
        if (mapVar.containsKey(id.lexeme())) return false;
        if (!mapFn.containsKey(id.lexeme())) return true;
        LinkedList a = mapFn.get(id.lexeme());
        if (a == null) return true;
        ListIterator listIterator = a.listIterator();
        while (listIterator.hasNext()) {
            Record rec = (Record) listIterator.next();
            if (rec.agree(list)) return false;
        }
        return true;
    }

    public boolean containsFn(Id id, ActualList list) {
        if (!mapFn.containsKey(id.lexeme())) return false;
        LinkedList a = mapFn.get(id.lexeme());
        if (a == null) return false;
        ListIterator listIterator = a.listIterator();
        while (listIterator.hasNext()) {
            Record rec = (Record) listIterator.next();
            if (rec.agree(list)) return true;
        }
        return false;
    }

    public void enterFn(Id id, Type type, FormalsList list) {
        LinkedList a = null;
        if (!mapFn.containsKey(id.lexeme())) {
            a = new LinkedList();
        } else {
            a = mapFn.get(id.lexeme());
        }
        a.addLast(new Record(type, list));
        mapFn.put(id.lexeme(), a);
    }

    public Type getTypeFn(Id id, ActualList list) {
        if (!mapFn.containsKey(id.lexeme())) return null;
        LinkedList a = mapFn.get(id.lexeme());
        if (a == null) return null;
        ListIterator listIterator = a.listIterator();
        while (listIterator.hasNext()) {
            Record rec = (Record) listIterator.next();
            if (rec.agree(list)) return rec.type;
        }
        return null;
    }
}






class Program extends Ast {
    public Program(DeclList declList) {
        this.declList = declList;
    }
    
    // Semantic checking
    public void check() {
        table = new SymbolTable();
        declList.table = table;
        declList.check();
    }

    private DeclList declList;
}

// **********************************************************************
// Decls
// **********************************************************************
class DeclList extends Ast {
    public DeclList(LinkedList decls) {
        this.decls = decls;
    }

    // linked list of kids (Decls)
    protected LinkedList decls;

    public void check() {
        ListIterator listIterator = this.decls.listIterator();
        while (listIterator.hasNext()) {
            Decl decl = (Decl) listIterator.next();
            decl.table = table;
            decl.check();
        }
    }
}

abstract class Decl extends Ast {
    public Type type;
    public Id name;
    public void check() {}
}

class VarDecl extends Decl {
    public VarDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }

    public void check() {
        if (!table.checkVar(name)) {
            Errors.semanticError(name.getLine(), name.getChar(), name.lexeme());
        } else {
            table.enterVar(name, type);
        }
    }
}

class FnDecl extends Decl {
    public FnDecl(Type type, Id name, FormalsList formalList, FnBody body) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
        this.body = body;
    }

    private FormalsList formalList;
    private FnBody body;

    public void check() {
        if (!table.checkFn(name, formalList)) {
            Errors.semanticError(name.getLine(), name.getChar(), name.lexeme());
        } else {
            table.enterFn(name, type, formalList);
        }
        formalList.table = new SymbolTable(table);
        formalList.check();

        body.table = formalList.table;
        body.table.scopeFn = type;
        body.check();
    }
}

class FnPreDecl extends Decl {
    public FnPreDecl(Type type, Id name, FormalsList formalList) {
        this.type = type;
        this.name = name;
        this.formalList = formalList;
    }

    private FormalsList formalList;

    public void check() {
        formalList.check();
    }
}

class FormalsList extends Ast {
    public FormalsList(LinkedList formals) {
        this.formals = formals;
    }

    // linked list of kids (FormalDecls)
    public LinkedList formals;

    public void check() {
        // params have unique name
        ListIterator listIterator = formals.listIterator();
        while (listIterator.hasNext()) {
            FormalDecl decl = (FormalDecl) listIterator.next();
            decl.table = table;
            decl.check();
        }
    }
}

class FormalDecl extends Decl {
    public FormalDecl(Type type, Id name) {
        this.type = type;
        this.name = name;
    }

    public void check() {
        if (!table.checkVar(name)) {
            Errors.semanticError(name.getLine(), name.getChar(), name.lexeme());
        } else {
            table.enterVar(name, type);
        }
    }
}

class FnBody extends Ast {
    public FnBody(DeclList declList, StmtList stmtList) {
        this.declList = declList;
        this.stmtList = stmtList;
    }

    private DeclList declList;
    private StmtList stmtList;

    public void check() {
        declList.table = table;
        declList.check();
        stmtList.table = table;
        stmtList.check();
    }
}

class StmtList extends Ast {
    public StmtList(LinkedList stmts) {
        this.stmts = stmts;
    }

    // linked list of kids (Stmts)
    private LinkedList stmts;

    public void check() {
        ListIterator listIterator = stmts.listIterator();
        while (listIterator.hasNext()) {
            Stmt stmt = (Stmt) listIterator.next();
            stmt.table = table;
            stmt.check();
        }
    }
}

// **********************************************************************
// Types
// **********************************************************************
class SimpleType extends Type {
    public SimpleType(String name) {
        this.name = name;
        elem = this;
    }

    public boolean isVoid() {
        return name.equals(Type.voidTypeName);
    }

    public boolean isInt() {
        return name.equals(Type.intTypeName);
    }

    public boolean agree(Type o) {
        if (o instanceof SimpleType) {
            return name.equals(((SimpleType)o).name);
        }
        return false;
    }
}

class ArrayType extends Type {
    public ArrayType(String name, int size) {
        this.name = name;
        this.size = size;
        elem = new SimpleType(name);
    }

    public ArrayType(String name, int size, int numPointers) {
        this.name = name;
        this.size = size;
        elem = new PointerType(name, numPointers);
    }

    public boolean isArray() {
        return true;
    }

    public boolean agree(Type o) {
        if (o instanceof ArrayType) {
            return elem.agree(((ArrayType)o).elem);
        }
        return false;
    }
}

class PointerType extends Type {
    public PointerType(String name, int numPointers) {
        this.elem = new SimpleType(name);
        for (int i = 1; i <= numPointers; ++i) {
            Type tmp = this.elem;
            this.elem = Type.CreatePointerType(tmp);
        }
    }

    public PointerType(Type elem) {
        this.elem = elem;
    }

    public boolean isPointer() {
        return true;
    }

    public Type DeRef() {
        return elem;
    }

    public boolean agree(Type o) {
        if (o instanceof PointerType) {
            return elem.agree(((PointerType)o).elem);
        }
        return false;
    }
}

class Type extends Ast {
    protected Type() {}
    
    public static Type CreateSimpleType(String name) {
        return new SimpleType(name);
    }

    public static Type CreateArrayType(String name, int size) {
        return new ArrayType(name, size);
    }

    public static Type CreatePointerType(String name, int numPointers) {
        return new PointerType(name, numPointers);
    }

    public static Type CreatePointerType(Type type) {
        return new PointerType(type);
    }

    public static Type CreateArrayPointerType(String name, int size, int numPointers) {
        return new ArrayType(name, size, numPointers);
    }
    
    public String name() {
        return name;
    }
 
    protected String name;
    protected int size;
    protected int numPointers;
    public Type elem;
    
    public static final String voidTypeName = "void";
    public static final String intTypeName = "int";
    public static final String stringTypeName = "string";
    public static final String undefinedTypeName = "undefined";

    public boolean isVoid() {
        return false;
    }

    public boolean isInt() {
        return false;
    }

    public boolean isArray() {
        return false;
    }

    public boolean isPointer() {
        return false;
    }

    public Type DeRef() {
        return null;
    }

    public boolean agree(Type o) {
        return false;
    }
}

// **********************************************************************
// Stmts
// **********************************************************************
abstract class Stmt extends Ast {
    public void check() {}
}

class AssignStmt extends Stmt {
    public AssignStmt(Exp lhs, Exp exp) {
        this.lhs = lhs;
        this.exp = exp;
    }

    private Exp lhs;
    private Exp exp;

    public void check() {
        lhs.table = table;
        lhs.check();
        exp.table = table;
        exp.check();

        if (!lhs.type.agree(exp.type)) {
        	System.out.println("A");
            Errors.semanticError(exp.getLine(), exp.getChar(), "not type agreement");
        }
    }
}

class IfStmt extends Stmt {
    public IfStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList = declList;
        this.stmtList = stmtList;
    }
    
    private Exp exp;
    private DeclList declList;
    private StmtList stmtList;

    public void check() {
        exp.table = table;
        exp.check();
        if (exp.type.isVoid()) {
            Errors.semanticWarn(exp.getLine(), exp.getChar(), "expression has type 'void'");
        }
        declList.table = new SymbolTable(table);
        declList.check();
        stmtList.table = declList.table;
        stmtList.check();
    }
}

class IfElseStmt extends Stmt {
    public IfElseStmt(Exp exp, DeclList declList1, StmtList stmtList1, 
            DeclList declList2, StmtList stmtList2) {
        this.exp = exp;
        this.declList1 = declList1;
        this.stmtList1 = stmtList1;
        this.declList2 = declList2;
        this.stmtList2 = stmtList2;
    }

    private Exp exp;
    private DeclList declList1;
    private DeclList declList2;
    private StmtList stmtList1;
    private StmtList stmtList2;

    public void check() {
        exp.table = table;
        exp.check();
        if (exp.type.isVoid()) {
            Errors.semanticWarn(exp.getLine(), exp.getChar(), "type of expression is 'void'");
        }

        declList1.table = new SymbolTable(table);
        declList1.check();
        stmtList1.table = declList1.table;
        stmtList1.check();

        declList2.table = new SymbolTable(table);
        declList2.check();
        stmtList2.table = declList2.table;
        stmtList2.check();
    }
}

class WhileStmt extends Stmt {
    public WhileStmt(Exp exp, DeclList declList, StmtList stmtList) {
        this.exp = exp;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    private Exp exp;
    private DeclList declList;
    private StmtList stmtList;

    public void check() {
        exp.table = table;
        exp.check();
        if (exp.type.isVoid()) {
            Errors.semanticWarn(exp.getLine(), exp.getChar(), "type of expression is 'void'");
        }

        declList.table = table;
        declList.check();
        stmtList.table = table;
        stmtList.check();
    }
}

class ForStmt extends Stmt {
    public ForStmt(Stmt init, Exp cond, Stmt incr, 
            DeclList declList, StmtList stmtList) {
        this.init = init;
        this.cond = cond;
        this.incr = incr;
        this.declList = declList;
        this.stmtList = stmtList;
    }

    private Stmt init;
    private Exp cond;
    private Stmt incr;
    private DeclList declList;
    private StmtList stmtList;

    public void check() {
        init.table = table;
        init.check();
        incr.table = table;
        incr.check();

        cond.table = table;
        cond.check();

        declList.table = table;
        declList.check();
        stmtList.table = table;
        stmtList.check();
    }
}

class CallStmt extends Stmt {
    public CallStmt(CallExp callExp) {
        this.callExp = callExp;
    }

    private CallExp callExp;

    public void check() {
        callExp.table = table;
        callExp.check();
    }
}

class ReturnStmt extends Stmt {
    public ReturnStmt(Exp exp) {
        this.exp = exp;
    }

    private Exp exp; // null for empty return

    public void check() {
        if (exp == null) { // void function
            if (!table.scopeFn.isVoid()) {
                Errors.semanticError(exp.getLine(), exp.getChar(), "mismatch return type");
            }
        } else { // int function
            exp.table = table;
            exp.check();

            if (!exp.type.agree(table.scopeFn)) {
                Errors.semanticError(exp.getLine(), exp.getChar(), "mismatch return type");
            }
        }
    }
}

// **********************************************************************
// Exps
// **********************************************************************
abstract class Exp extends Ast {
    public Type type;
    public abstract int getLine();
    public abstract int getChar();
    public void check() {}
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
    public IntLit(int lineNum, int charNum, int intVal) {
        super(lineNum, charNum);
        this.intVal = intVal;
        type = Type.CreateSimpleType(Type.intTypeName);
    }

    private int intVal;
}

class StringLit extends BasicExp {
    public StringLit(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
        type = Type.CreateSimpleType(Type.stringTypeName);
    }

    public String str() {
        return strVal;
    }
    
    private String strVal;
}

class Id extends BasicExp {
    public Id(int lineNum, int charNum, String strVal) {
        super(lineNum, charNum);
        this.strVal = strVal;
    }

    public String lexeme() {
        return strVal;
    }

    private String strVal;

    public void check() {
        type = table.getTypeVar(this);
        if (type == null) {
            Errors.semanticError(getLine(), getChar(), lexeme());
            type = Type.CreateSimpleType(Type.undefinedTypeName);
        }
    }
}

class ArrayExp extends Exp {
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

    private Exp lhs;
    private Exp exp;

    public void check() {
        lhs.table = table;
        lhs.check();
        if (!lhs.type.isArray()) {
            Errors.semanticError(lhs.getLine(), lhs.getChar(), "not array");
            type = Type.CreateSimpleType(Type.undefinedTypeName);
        } else {
            type = lhs.type.elem;
        }

        exp.table = table;
        exp.check();
        if (!exp.type.isInt()) {
            Errors.semanticError(exp.getLine(), exp.getLine(), "not integer");
        }
    }
}

class CallExp extends Exp {
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

    private Id name;
    private ActualList actualList;

    public void check() {
        ListIterator it = actualList.exps.listIterator();
        while (it.hasNext()) {
            Exp e = (Exp) it.next();
            e.table = table;
            e.check();
        }

        type = table.getTypeFn(name, actualList);
        if (type == null) {
            Errors.semanticError(name.getLine(), name.getChar(), name.lexeme() + " undefined");
            type = Type.CreateSimpleType(Type.undefinedTypeName);
        }
    }
}

class ActualList extends Ast {
    public ActualList(LinkedList exps) {
        this.exps = exps;
    }

    // linked list of kids (Exps)
    public LinkedList exps;
}

abstract class UnaryExp extends Exp {
    public UnaryExp(Exp exp) {
        this.exp = exp;
    }

    public int getLine() {
        return exp.getLine();
    }

    public int getChar() {
        return exp.getChar();
    }

    protected Exp exp;
}

abstract class BinaryExp extends Exp {
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

    protected Exp exp1;
    protected Exp exp2;

    public void check() {
        exp1.table = table;
        exp1.check();
        exp2.table = table;
        exp2.check();

        if (!exp1.type.isInt() || !exp2.type.isInt()) {
            Errors.semanticError(getLine(), getChar(), "invalid types");
            type = Type.CreateSimpleType(Type.undefinedTypeName);
        } else {
            type = exp1.type;
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

    public void check() {
        exp.table = table;
        exp.check();
        if (!exp.type.isInt()) {
            Errors.semanticError(getLine(), getChar(), "invalid type");
        }
        type = exp.type;
    }
}

class NotExp extends UnaryExp {
    public NotExp(Exp exp) {
        super(exp);
    }

    public void check() {
        exp.table = table;
        exp.check();
        if (!exp.type.isInt()) {
            Errors.semanticError(getLine(), getChar(), "invalid type");
        }
        type = exp.type;
    }
}

class AddrOfExp extends UnaryExp {
    public AddrOfExp(Exp exp) {
        super(exp);
    }

    public void check() {
        exp.table = table;
        exp.check();
        type = Type.CreatePointerType(exp.type);
    }
}

class DeRefExp extends UnaryExp {
    public DeRefExp(Exp exp) {
        super(exp);
    }

    public void check() {
        exp.table = table;
        exp.check();
        if (!exp.type.isPointer()) {
            Errors.semanticError(getLine(), getChar(), "invalid type");
        }
        type = exp.type.DeRef();
    }
}

// **********************************************************************
// BinaryExps
// **********************************************************************
class PlusExp extends BinaryExp {
    public PlusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class MinusExp extends BinaryExp {
    public MinusExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class TimesExp extends BinaryExp {
    public TimesExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class DivideExp extends BinaryExp {
    public DivideExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class ModuloExp extends BinaryExp {
    public ModuloExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class AndExp extends BinaryExp {
    public AndExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class OrExp extends BinaryExp {
    public OrExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class EqualsExp extends BinaryExp {
    public EqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class NotEqualsExp extends BinaryExp {
    public NotEqualsExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class LessExp extends BinaryExp {
    public LessExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class GreaterExp extends BinaryExp {
    public GreaterExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class LessEqExp extends BinaryExp {
    public LessEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}

class GreaterEqExp extends BinaryExp {
    public GreaterEqExp(Exp exp1, Exp exp2) {
        super(exp1, exp2);
    }
}