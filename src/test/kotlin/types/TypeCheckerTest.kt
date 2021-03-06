package types

import kotlinx.collections.immutable.persistentHashMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import syntax.*

internal class TypeCheckerTest {

    var typeChecker = TypeChecker()

    @BeforeEach
    fun setUp() {
        typeChecker.freshSupply = 0
        typeChecker.substitution = Substitution(hashMapOf())
    }

    @Test
    @Tag("literal")
    fun `it infers the type of an Int literal`() {
        "42" hasType "Int"
    }

    @Test
    @Tag("literal")
    fun `it infers the type of a bool literal`() {
        "true" hasType "Bool"
    }

    @Test
    @Tag("literal")
    fun `it infers the type of a String literal`() {
        "\"Hello :)\"" hasType "String"
    }

    @Test
    @Tag("var")
    fun `it infers the type of a variable that exists in the Environment`() {
        val env = given("x" to "Int", "y" to "String")

        "x" inEnvironment env hasType "Int"
        "y" inEnvironment env hasType "String"
    }

    @Test
    @Tag("var")
    fun `it fails to infer the type of a variable that doesn't exist in the Environment`() {
        "x" failsToTypecheckWith "Unknown variable x"
        "y" failsToTypecheckWith "Unknown variable y"
    }

    @Test
    @Tag("let")
    fun `it infers the type of a simple let`() {
        """
            let x = 42 in 
            "Hello"
        """ hasType "String"
    }

    @Test
    @Tag("let")
    fun `it infers the type of a let bound variable`() {
        """
            let x = 42 in 
            x
        """ hasType "Int"
    }

    @Test
    @Tag("let")
    fun `it infers the type of a nested let bound variable`() {
        """
            let x = true in
            let y = 42 in
            x
        """ hasType "Bool"
    }

    @Test
    @Tag("let")
    fun `it infers the type of a shadowed let bound variable`() {
        """
            let x = true in
            let x = 42 in
            x
        """ hasType "Int"
    }

    @Test
    @Tag("let")
    fun `it fails to infer the type of an out of scope let bound variable`() {
        """
            let x = 
                let y = 42 in
                y in
            y
        """ failsToTypecheckWith "Unknown variable y"
    }

    @Test
    @Tag("let")
    fun `it handles a nesting and shadowing let`() {
        """
            let y = "Hello" in
            let x = 
                let y = 42 in
                y in
            y
        """ hasType "String"
    }

    @Test
    @Tag("lambda")
    fun `it infers the type of a simple Lambda`() {
        """
            \x -> 42
        """ hasType "u1 -> Int"
    }

    @Test
    @Tag("lambda")
    fun `it infers the type of a Lambda that uses its argument`() {
        """
            \x -> x
        """ hasType "u1 -> u1"
    }

    @Test
    @Tag("lambda")
    fun `it infers the type of a nested Lambda`() {
        """
            \x -> (\y -> x)
        """ hasType "u1 -> u2 -> u1"
    }

    @Test
    @Tag("lambda")
    fun `it infers the type of a nested Lambda that shadows a variable`() {
        """
            \x -> (\x -> x)
        """ hasType "u1 -> u2 -> u2"
    }

    @Test
    @Tag("application")
    fun `it figures out a simple application`() {
        val env = given("myFunc" to "Bool -> Int")

        "myFunc true" inEnvironment env hasType "Int"
    }

    @Test
    @Tag("application")
    fun `it handles curried application`() {
        val env = given("add" to "Int -> Int -> Int")

        "add 1 2" inEnvironment env hasType "Int"
    }

    @Test
    @Tag("application")
    fun `it fails on an ill-typed application`() {
        val env = given("myFunc" to "Bool -> Int")

        "myFunc 10" inEnvironment env failsToTypecheckWith UnificationFailure("Bool", "Int")
    }

    @Test
    @Tag("application")
    fun `let binding identity`() {
        """
            let identity = \x -> x in
            identity 5
        """ hasType "Int"
    }

    @Test
    @Tag("application")
    fun `let binding const`() {
        """
            let const = \x -> \y -> x in
            const 5 true
        """ hasType "Int"
    }

    @Test
    @Tag("application")
    fun `it handles higher order functions like flip`() {
        """
            let flip = \f -> \x -> \y -> f y x in 
            let const = \x -> \y -> x in
            flip const 5 true
        """ hasType "Bool"
    }

    @Test
    @Tag("if")
    fun `it typechecks simple ifs`() {
        "if true then 1 else 0" hasType "Int"
        "if false then true else false" hasType "Bool"
    }

    @Test
    @Tag("if")
    fun `ifs determine their predicate to have type Bool`() {
        """
            \x -> if x then 1 else 0
        """ hasType "Bool -> Int"
    }

    @Test
    @Tag("if")
    fun `it fails on a non-boolean predicate`() {
        "if 1 then 0 else 1" failsToTypecheckWith UnificationFailure("Bool", "Int")
    }

    @Test
    @Tag("if")
    fun `it fails on mismatched branches`() {
        """
            if true then 
                0 
            else
                "Hello"
        """ failsToTypecheckWith UnificationFailure("String", "Int")
    }

    @Test
    @Tag("recursivelet")
    fun `it infers a recursive let`() {
        val env = given(
            "eq_int" to "Int -> Int -> Bool",
            "add" to "Int -> Int -> Int",
            "sub" to "Int -> Int -> Int"
        )
        """
            let sum = \x -> if eq_int x 0 then 0 else add x (sum (sub x 1)) in
            sum 3
        """ inEnvironment env hasType "Int"
    }

    @Test
    @Tag("recursivelet")
    fun `it fails to typecheck a wrong recursive let`() {
        val env = given("add" to "Int -> Int -> Int")
        """
            let fail = \x -> add fail 10 in
            fail 3
        """ inEnvironment env failsToTypecheckWith UnificationFailure("u2 -> Int", "Int")
        // Hard to give the precise error here. Just make sure it fails with a sensible error
    }














    // ================ Testing DSL from here ================

    private fun given(vararg givens: Pair<String, String>): Environment {
        val givensList = givens.map { (v, ty) -> Name(v) to Parser.parseTestType(ty) }
        return Environment(persistentHashMapOf(*givensList.toTypedArray()))
    }

    private infix fun String.hasType(tyString: String) {
        val expr = Parser.parseExpression(this)
        val ty = Parser.parseTestType(tyString)
        val inferred = typeChecker.inferExpr(Environment(), expr)

        assertEquals(ty, inferred) {
            "$this\n  was inferred to have type:\n${inferred.pretty()}\n  but should have type:\n$tyString\n"
        }
    }

    private infix fun String.failsToTypecheckWith(message: String) {
        val expr = Parser.parseExpression(this)
        val exception: Exception = assertThrows(Exception::class.java) {
            typeChecker.inferExpr(Environment(), expr)
        }
        assertEquals(message, exception.message)
    }

    private data class UnificationFailure(val ty1: String, val ty2: String)

    private infix fun String.failsToTypecheckWith(u: UnificationFailure) {
        val expr = Parser.parseExpression(this)
        val exception: Exception = assertThrows(Exception::class.java) {
            typeChecker.inferExpr(Environment(), expr)
        }
        assertEquals(
            true,
            exception.message == "Can't match ${u.ty1} with ${u.ty2}" ||
                    exception.message == "Can't match ${u.ty2} with ${u.ty1}"
        ) {
            "$this\n  should have failed to unify\n${u.ty1} with ${u.ty2}\n  but instead failed with\n${exception.message}\n"
        }
    }
    private infix fun ExprWithEnv.hasType(tyString: String) {
        val ty = Parser.parseTestType(tyString)
        val inferred = typeChecker.inferExpr(env, expr)

        assertEquals(ty, inferred) {
            "$this\n  was inferred to have type:\n${inferred.pretty()}\n  but should have type:\n$tyString\n"
        }
    }

    private infix fun String.inEnvironment(env: Environment): ExprWithEnv =
        ExprWithEnv(env, Parser.parseExpression(this))

    private infix fun ExprWithEnv.failsToTypecheckWith(message: String) {
        val exception: Exception = assertThrows(Exception::class.java) {
            typeChecker.inferExpr(env, expr)
        }
        assertEquals(message, exception.message)
    }

    private infix fun ExprWithEnv.failsToTypecheckWith(u: UnificationFailure) {
        val exception: Exception = assertThrows(Exception::class.java) {
            typeChecker.inferExpr(env, expr)
        }
        assertEquals(
            true,
            exception.message == "Can't match ${u.ty1} with ${u.ty2}" ||
                    exception.message == "Can't match ${u.ty2} with ${u.ty1}"
        ) {
            "$this\n  should have failed to unify\n${u.ty1} with ${u.ty2}\n  but instead failed with\n${exception.message}\n"
        }
    }

}

data class ExprWithEnv(val env: Environment, val expr: Expression)




