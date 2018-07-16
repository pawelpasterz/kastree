package kastree.ast

sealed class Node {
    var tag: Any? = null

    interface WithAnnotations {
        val anns: List<Modifier.AnnotationSet>
    }

    interface WithModifiers : WithAnnotations {
        val mods: List<Modifier>
        override val anns: List<Modifier.AnnotationSet> get() = mods.mapNotNull { it as? Modifier.AnnotationSet }
    }

    interface Entry : WithAnnotations {
        val pkg: Package?
        val imports: List<Import>
    }

    data class File(
        override val anns: List<Modifier.AnnotationSet>,
        override val pkg: Package?,
        override val imports: List<Import>,
        val decls: List<Decl>
    ) : Node(), Entry

    data class Script(
        override val anns: List<Modifier.AnnotationSet>,
        override val pkg: Package?,
        override val imports: List<Import>,
        val exprs: List<Expr>
    ) : Node(), Entry

    data class Package(
        override val mods: List<Modifier>,
        val names: List<String>
    ) : Node(), WithModifiers

    data class Import(
        val names: List<String>,
        val wildcard: Boolean,
        val alias: String?
    ) : Node()

    sealed class Decl : Node() {
        data class Structured(
            override val mods: List<Modifier>,
            val form: Form,
            val name: String,
            val typeParams: List<TypeParam>,
            val primaryConstructor: PrimaryConstructor?,
            val parentAnns: List<Modifier.AnnotationSet>,
            val parents: List<Parent>,
            val typeConstraints: List<TypeConstraint>,
            // TODO: Can include primary constructor
            val members: List<Decl>
        ) : Decl(), WithModifiers {
            enum class Form {
                CLASS, ENUM_CLASS, INTERFACE, OBJECT, COMPANION_OBJECT
            }
            sealed class Parent : Node() {
                data class CallConstructor(
                    val type: TypeRef.Simple,
                    val typeArgs: List<Node.Type?>,
                    val args: List<ValueArg>,
                    val lambda: Expr.Call.TrailLambda?
                ) : Parent()
                data class Type(
                    val type: TypeRef.Simple,
                    val by: Expr?
                ) : Parent()
            }
            data class PrimaryConstructor(
                override val mods: List<Modifier>,
                val params: List<Func.Param>
            ) : Node(), WithModifiers
        }
        data class Init(val stmts: List<Stmt>) : Decl()
        data class Func(
            override val mods: List<Modifier>,
            val typeParams: List<TypeParam>,
            val receiverType: Type?,
            val name: String,
            val paramTypeParams: List<TypeParam>,
            val params: List<Func.Param>,
            val type: Type?,
            val typeConstraints: List<TypeConstraint>,
            val body: Body?
        ) : Decl(), WithModifiers {
            data class Param(
                override val mods: List<Modifier>,
                val readOnly: Boolean?,
                val name: String,
                val type: Type,
                val default: Expr?
            ) : Node(), WithModifiers
            sealed class Body : Node() {
                data class Block(val stmts: List<Stmt>) : Body()
                data class Expr(val expr: Node.Expr) : Body()
            }
        }
        data class Property(
            override val mods: List<Modifier>,
            val readOnly: Boolean,
            val typeParams: List<TypeParam>,
            val receiverType: Type?,
            // Always at least one, more than one is destructuring, null is underscore in destructure
            val vars: List<Var?>,
            val typeConstraints: List<TypeConstraint>,
            val delegated: Boolean,
            val expr: Expr?,
            val accessors: Pair<Accessor, Accessor?>?
        ) : Decl(), WithModifiers {
            data class Var(
                val name: String,
                val type: Type?
            ) : Node()

            sealed class Accessor : Node(), WithModifiers {
                data class Get(
                    override val mods: List<Modifier>,
                    val type: Type?,
                    val body: Func.Body?
                ) : Accessor()
                data class Set(
                    override val mods: List<Modifier>,
                    val paramMods: List<Modifier>,
                    val paramName: String?,
                    val paramType: Type?,
                    val body: Func.Body?
                ) : Accessor()
            }
        }
        data class TypeAlias(
            override val mods: List<Modifier>,
            val typeParams: List<TypeParam>,
            val type: Type
        ) : Decl(), WithModifiers
        data class Constructor(
            override val mods: List<Modifier>,
            val params: List<Func.Param>,
            val delegationCall: DelegationCall?,
            val stmts: List<Stmt>
        ) : Decl(), WithModifiers {
            data class DelegationCall(
                val target: DelegationTarget,
                val args: List<ValueArg>
            ) : Node()
            enum class DelegationTarget { THIS, SUPER }
        }
        data class EnumEntry(
            override val mods: List<Modifier>,
            val name: String,
            val args: List<ValueArg>,
            val members: List<Decl>
        ) : Decl(), WithModifiers
    }

    data class TypeParam(
        override val mods: List<Modifier>,
        val name: String,
        val type: TypeRef.Simple?
    ) : Node(), WithModifiers

    data class TypeConstraint(
        override val anns: List<Modifier.AnnotationSet>,
        val name: String,
        val type: Type
    ) : Node(), WithAnnotations

    sealed class TypeRef : Node() {
        data class Paren(val type: TypeRef) : TypeRef()
        data class Func(
            val receiverType: Type?,
            val params: List<Param>,
            val type: Type
        ) : TypeRef() {
            data class Param(
                val name: String?,
                val type: Type
            ) : Node()
        }
        data class Simple(
            val name: String,
            // Null means any
            val typeParams: List<Type?>
        ) : TypeRef()
        data class Nullable(val type: TypeRef) : TypeRef()
        data class Dynamic(val _unused_: Boolean = false) : TypeRef()
    }

    data class Type(
        override val mods: List<Modifier>,
        val ref: TypeRef
    ) : Node(), WithModifiers

    data class ValueArg(
        val name: String?,
        val asterisk: Boolean,
        val expr: Expr
    ) : Node()

    sealed class Expr : Node() {
        data class If(
            val expr: Expr,
            val body: Expr,
            val elseBody: Expr?
        ) : Expr()
        data class Try(
            val stmts: List<Stmt>,
            val catches: List<Catch>,
            val finallyStmts: List<Stmt>
        ) : Expr() {
            data class Catch(
                override val anns: List<Modifier.AnnotationSet>,
                val varName: String,
                val varType: TypeRef.Simple,
                val stmts: List<Stmt>
            ) : Node(), WithAnnotations
        }
        data class For(
            override val anns: List<Modifier.AnnotationSet>,
            // More than one means destructure, null means underscore
            val vars: List<Decl.Property.Var?>,
            val inExpr: Expr,
            val body: Expr
        ) : Expr(), WithAnnotations
        data class While(
            val expr: Expr,
            val body: Expr,
            val doWhile: Boolean
        ) : Expr()
        data class BinaryOp(
            val lhs: Expr,
            val oper: Oper,
            val rhs: Expr
        ) : Expr() {
            sealed class Oper : Node() {
                data class Infix(val str: String) : Oper()
                data class Token(val token: BinaryOp.Token) : Oper()
            }
            enum class Token(val str: String) {
                MUL("*"), DIV("/"), MOD("%"), ADD("+"), SUB("-"),
                IN("in"), NOT_IN("!in"),
                GT(">"), GTE(">="), LT("<"), LTE("<="),
                EQ("=="), NEQ("!="),
                ASSN("="), MUL_ASSN("*="), DIV_ASSN("/="), MOD_ASSN("%="), ADD_ASSN("+="), SUB_ASSN("+="),
                OR("||"), AND("&&"), ELVIS("?:"), RANGE(".."),
                DOT("."), DOT_SAFE("?."), SAFE("?")
            }
        }
        data class UnaryOp(
            val expr: Expr,
            val oper: Oper,
            val prefix: Boolean
        ) : Expr() {
            data class Oper(val token: Token) : Node()
            enum class Token(val str: String) {
                NEG("-"), POS("+"), INC("++"), DEC("--"), NOT("!"), NULL_DEREF("!!")
            }
        }
        data class TypeOp(
            val lhs: Expr,
            val oper: Oper,
            val rhs: Type
        ) : Expr() {
            data class Oper(val token: Token) : Node()
            enum class Token(val str: String) {
                AS("as"), AS_SAFE("as?"), COL(":"), IS("is"), NOT_IS("!is")
            }
        }
        data class CallableRef(
            val expr: Expr?,
            val name: String
        ) : Expr()
        data class ClassLit(
            val expr: Expr?
        ) : Expr()
        data class Paren(
            val expr: Expr
        ) : Expr()
        data class StringTmpl(
            val elems: List<Elem>
        ) : Expr() {
            sealed class Elem : Node() {
                data class Regular(val str: String) : Elem()
                data class ShortTmpl(val str: String) : Elem()
                data class UnicodeEsc(val digits: String) : Elem()
                data class RegularEsc(val char: kotlin.Char) : Elem()
                data class LongTmpl(val expr: Expr) : Elem()
            }
        }
        data class Const(
            val value: String,
            val form: Form
        ) : Expr() {
            enum class Form { BOOLEAN, CHAR, INT, FLOAT, NULL }
        }
        data class Brace(
            val params: List<Param>,
            val stmts: List<Stmt>
        ) : Expr() {
            data class Param(
                // Multiple means destructure, null means underscore
                val vars: List<Decl.Property.Var?>,
                val destructType: Type?
            ) : Expr()
        }
        data class This(
            val label: String?
        ) : Expr()
        data class Super(
            val typeArg: Type?,
            val label: String?
        ) : Expr()
        data class When(
            val expr: Expr?,
            val entries: List<Entry>
        ) : Expr() {
            data class Entry(
                val conds: List<Cond>,
                val body: Expr
            ) : Node()
            sealed class Cond : Node() {
                data class Expr(val expr: Node.Expr) : Cond()
                data class In(
                    val expr: Node.Expr,
                    val not: Boolean
                ) : Cond()
                data class Is(
                    val type: Type,
                    val not: Boolean
                ) : Cond()
            }
        }
        data class Object(
            val parents: List<Decl.Structured.Parent>,
            val members: List<Decl>
        ) : Expr()
        data class Throw(
            val expr: Expr
        ) : Expr()
        data class Return(
            val label: String?,
            val expr: Expr?
        ) : Expr()
        data class Continue(
            val label: String?
        ) : Expr()
        data class Break(
            val label: String?
        ) : Expr()
        data class CollLit(
            val exprs: List<Expr>
        ) : Expr()
        data class Name(
            val name: String
        ) : Expr()
        data class Labeled(
            val label: String,
            val expr: Expr
        ) : Expr()
        data class Annotated(
            override val anns: List<Modifier.AnnotationSet>,
            val expr: Expr
        ) : Expr(), WithAnnotations
        data class Call(
            val expr: Expr,
            val typeArgs: List<Type?>,
            val args: List<ValueArg>,
            val lambda: TrailLambda?
        ) : Expr() {
            data class TrailLambda(
                override val anns: List<Modifier.AnnotationSet>,
                val label: String?,
                val func: Brace
            ) : Node(), WithAnnotations
        }
        data class ArrayAccess(
            val expr: Expr,
            val indices: List<Expr>
        ) : Expr()
    }

    sealed class Stmt : Node() {
        data class Decl(val decl: Node.Decl) : Stmt()
        data class Expr(val expr: Node.Expr) : Stmt()
    }

    sealed class Modifier : Node() {
        data class AnnotationSet(
            val target: Target?,
            val anns: List<Annotation>
        ) : Modifier() {
            enum class Target {
                FIELD, FILE, PROPERTY, GET, SET, RECEIVER, PARAM, SETPARAM, DELEGATE
            }
            data class Annotation(
                val names: List<String>,
                val typeArgs: List<Type>,
                val args: List<ValueArg>
            ) : Node()
        }
        data class Lit(val keyword: Keyword) : Modifier()
        enum class Keyword {
            ABSTRACT, FINAL, OPEN, ANNOTATION, SEALED, DATA, OVERRIDE, LATEINIT,
            PRIVATE, PROTECTED, PUBLIC, INTERNAL,
            IN, OUT, NOINLINE, CROSSINLINE, VARARG, REIFIED,
            TAILREC, OPERATOR, INFIX, INLINE, EXTERNAL, SUSPEND, CONST
        }
    }
}