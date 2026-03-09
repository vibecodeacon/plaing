package dev.plaing.compiler.parser

/**
 * AST nodes for the plaing language.
 */

// Source location for error reporting
data class SourceLocation(val line: Int, val column: Int, val file: String = "")

// Top-level program
data class PlaingProgram(val declarations: List<Declaration>)

// All top-level declarations
sealed class Declaration {
    abstract val location: SourceLocation
}

// --- Types ---

sealed class PlaingType {
    data object TextType : PlaingType()
    data object NumberType : PlaingType()
    data object BooleanType : PlaingType()
    data object DateType : PlaingType()
    data class ListType(val elementType: PlaingType) : PlaingType()
    data class OptionalType(val innerType: PlaingType) : PlaingType()
    data class EntityRef(val name: String) : PlaingType()
}

// --- Entity ---

data class EntityDeclaration(
    val name: String,
    val fields: List<FieldDefinition>,
    override val location: SourceLocation
) : Declaration()

data class FieldDefinition(
    val name: String,
    val type: PlaingType,
    val modifiers: List<FieldModifier>,
    val location: SourceLocation
)

sealed class FieldModifier {
    data object Required : FieldModifier()
    data object Unique : FieldModifier()
    data object Hidden : FieldModifier()
    data class Default(val value: Expression) : FieldModifier()
}

// --- Event ---

data class EventDeclaration(
    val name: String,
    val fields: List<EventField>,
    override val location: SourceLocation
) : Declaration()

data class EventField(
    val name: String,
    val type: PlaingType,
    val location: SourceLocation
)

// --- Handler ---

data class HandlerDeclaration(
    val eventName: String,
    val body: List<Statement>,
    override val location: SourceLocation
) : Declaration()

// --- Page ---

data class PageDeclaration(
    val name: String,
    val body: List<UiElement>,
    override val location: SourceLocation
) : Declaration()

// --- Client Reaction ---

data class ReactionDeclaration(
    val eventName: String,
    val actions: List<ReactionAction>,
    override val location: SourceLocation
) : Declaration()

// --- Style ---

data class StyleDeclaration(
    val targetName: String,
    val properties: List<StyleProperty>,
    val pseudoStates: List<PseudoStateBlock>,
    override val location: SourceLocation
) : Declaration()

data class StyleProperty(
    val name: String,  // e.g. "background color", "border radius"
    val value: String,  // e.g. "white", "8px", "24px"
    val location: SourceLocation
)

data class PseudoStateBlock(
    val state: String,  // e.g. "hover", "focus"
    val properties: List<StyleProperty>,
    val location: SourceLocation
)

// --- Expressions ---

sealed class Expression {
    abstract val location: SourceLocation
}

data class StringLiteral(val value: String, override val location: SourceLocation) : Expression()
data class NumberLiteral(val value: Double, override val location: SourceLocation) : Expression()
data class BooleanLiteral(val value: Boolean, override val location: SourceLocation) : Expression()
data class Identifier(val name: String, override val location: SourceLocation) : Expression()
data class DotAccess(val target: Expression, val field: String, override val location: SourceLocation) : Expression()
data class NowLiteral(override val location: SourceLocation) : Expression()

// --- Statements (used in handlers) ---

sealed class Statement {
    abstract val location: SourceLocation
}

// find User where email is "..."
data class FindStatement(
    val entityName: String,
    val all: Boolean,  // find vs find all
    val conditions: List<WhereCondition>,
    override val location: SourceLocation
) : Statement()

// create User with name = "Alice", email = "..."
data class CreateStatement(
    val entityName: String,
    val forEntity: String?,  // "create Session for User"
    val assignments: List<Assignment>,
    override val location: SourceLocation
) : Statement()

// update User set role = "admin" where email is "..."
data class UpdateStatement(
    val entityName: String,
    val assignments: List<Assignment>,
    val conditions: List<WhereCondition>,
    override val location: SourceLocation
) : Statement()

// delete Session where expires_at is before now
data class DeleteStatement(
    val entityName: String,
    val conditions: List<WhereCondition>,
    override val location: SourceLocation
) : Statement()

// emit LOGIN_SUCCESS with user = User, token = Session.token
data class EmitStatement(
    val eventName: String,
    val arguments: List<Assignment>,
    override val location: SourceLocation
) : Statement()

// if / otherwise
data class IfStatement(
    val condition: Condition,
    val body: List<Statement>,
    val otherwise: List<Statement>?,
    override val location: SourceLocation
) : Statement()

// stop (early return)
data class StopStatement(override val location: SourceLocation) : Statement()

// --- Conditions ---

sealed class Condition {
    abstract val location: SourceLocation
}

// "no User found"
data class NoResultCondition(
    val entityName: String,
    override val location: SourceLocation
) : Condition()

// "User.password matches LOGIN_ATTEMPT.password"
data class ComparisonCondition(
    val left: Expression,
    val operator: ComparisonOperator,
    val right: Expression,
    override val location: SourceLocation
) : Condition()

enum class ComparisonOperator {
    IS, IS_NOT, MATCHES,
    IS_AFTER, IS_BEFORE,
    CONTAINS, STARTS_WITH,
    IS_GREATER_THAN, IS_LESS_THAN
}

// --- Where conditions (for data operations) ---

data class WhereCondition(
    val field: String,
    val operator: ComparisonOperator,
    val value: Expression,
    val location: SourceLocation
)

// --- Assignments ---

data class Assignment(
    val name: String,
    val value: Expression,
    val location: SourceLocation
)

// --- UI Elements ---

sealed class UiElement {
    abstract val location: SourceLocation
}

data class LayoutElement(
    val name: String,
    val children: List<UiElement>,
    override val location: SourceLocation
) : UiElement()

data class HeadingElement(
    val text: String,
    override val location: SourceLocation
) : UiElement()

data class FormElement(
    val name: String,
    val children: List<UiElement>,
    override val location: SourceLocation
) : UiElement()

data class InputElement(
    val name: String,
    val properties: List<InputProperty>,
    override val location: SourceLocation
) : UiElement()

data class TextElement(
    val value: Expression,
    override val location: SourceLocation
) : UiElement()

data class ListElement(
    val name: String,
    val entityName: String,
    val fields: List<String>,
    override val location: SourceLocation
) : UiElement()

data class ButtonElement(
    val text: String,
    val action: ButtonAction?,
    override val location: SourceLocation
) : UiElement()

// --- Input Properties ---

sealed class InputProperty {
    data class Placeholder(val text: String) : InputProperty()
    data class Type(val typeName: String) : InputProperty()
    data class BindsTo(val field: String) : InputProperty()
}

// --- Button Action ---

data class ButtonAction(
    val eventName: String,
    val arguments: List<String>,
    val location: SourceLocation
)

// --- Reaction Actions ---

sealed class ReactionAction {
    abstract val location: SourceLocation
}

data class StoreAction(
    val entityName: String,
    val from: Expression,
    override val location: SourceLocation
) : ReactionAction()

data class StoreAllAction(
    val entityName: String,
    val from: Expression,
    override val location: SourceLocation
) : ReactionAction()

data class NavigateAction(
    val targetPage: String,
    override val location: SourceLocation
) : ReactionAction()

data class ShowAlertAction(
    val message: Expression,
    val targetPage: String,
    override val location: SourceLocation
) : ReactionAction()
