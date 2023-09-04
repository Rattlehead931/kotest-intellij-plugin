package io.kotest.plugin.intellij.styles

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.kotest.plugin.intellij.Test
import io.kotest.plugin.intellij.TestName
import io.kotest.plugin.intellij.TestType
import io.kotest.plugin.intellij.psi.extractLhsStringArgForDotExpressionWithRhsFinalLambda
import io.kotest.plugin.intellij.psi.extractStringArgForFunctionWithStringAndLambdaArgs
import io.kotest.plugin.intellij.psi.ifCallExpressionLambdaOpenBrace
import io.kotest.plugin.intellij.psi.ifDotExpressionSeparator
import io.kotest.plugin.intellij.psi.ifOpenQuoteOfFunctionName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

object DescribeSpecStyle : SpecStyle {

   override fun fqn() = FqName("io.kotest.core.spec.style.DescribeSpec")

   override fun specStyleName(): String = "Describe Spec"

   override fun generateTest(specName: String, name: String): String {
      return "describe(\"$name\") { }"
   }

   private val fnNames = setOf("describe", "xdescribe", "context", "xcontext", "it", "xit")

   override fun isTestElement(element: PsiElement): Boolean = test(element) != null

   private fun locateParent(element: PsiElement): Test? {
      // if parent is null then we have hit the end
      val p = element.parent ?: return null
      fun tryDots() =
         if (p is KtCallExpression) p.tryDescribe() ?: p.tryXDescribe() ?: p.tryContext() ?: p.tryXContent() else null

      fun tryCalls() =
         if (p is KtDotQualifiedExpression) p.tryDescribeWithConfig() ?: p.tryXDescribeWithConfig() else null
      return tryDots() ?: tryCalls() ?: locateParent(p)
   }

   /**
    * Finds tests in the form:
    *
    *   describe("test name") { }
    *
    */
   private fun KtCallExpression.tryDescribe(): Test? {
      val name = extractStringArgForFunctionWithStringAndLambdaArgs("describe") ?: return null
      return buildTest(
         TestName(null, name.text, name.interpolated),
         name.text.startsWith("!"),
         this,
         TestType.Container
      )
   }

   /**
    * Finds tests in the form:
    *
    *   context("test name") { }
    *
    */
   private fun KtCallExpression.tryContext(): Test? {
      val name = extractStringArgForFunctionWithStringAndLambdaArgs("context") ?: return null
      return buildTest(
         TestName(null, name.text, name.interpolated),
         name.text.startsWith("!"),
         this,
         TestType.Container
      )
   }

   /**
    * Finds tests in the form:
    *
    *   it("test name") { }
    *
    */
   private fun KtCallExpression.tryIt(): Test? {
      val name = extractStringArgForFunctionWithStringAndLambdaArgs("it") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), name.text.startsWith("!"), this, TestType.Test)
   }

   /**
    * Finds tests in the form:
    *
    *   xit("test name") { }
    *
    */
   private fun KtCallExpression.tryXIt(): Test? {
      val name = extractStringArgForFunctionWithStringAndLambdaArgs("xit") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), true, this, TestType.Test)
   }

   /**
    * Finds tests in the form:
    *
    *   xdescribe("test name") { }
    *
    */
   private fun KtCallExpression.tryXDescribe(): Test? {
      val name = extractStringArgForFunctionWithStringAndLambdaArgs("xdescribe") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), true, this, TestType.Container)
   }

   /**
    * Finds tests in the form:
    *
    *   xcontext("test name") { }
    *
    */
   private fun KtCallExpression.tryXContent(): Test? {
      val name = extractStringArgForFunctionWithStringAndLambdaArgs("xcontext") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), true, this, TestType.Container)
   }

   /**
    * Finds tests in the form:
    *
    *   it("test name").config { }
    *
    */
   private fun KtDotQualifiedExpression.tryItWithConfig(): Test? {
      val name = extractLhsStringArgForDotExpressionWithRhsFinalLambda("it", "config") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), name.text.startsWith("!"), this, TestType.Test)
   }

   /**
    * Finds tests in the form:
    *
    *   xit("test name").config(...) { }
    *
    */
   private fun KtDotQualifiedExpression.tryXItWithConfig(): Test? {
      val name = extractLhsStringArgForDotExpressionWithRhsFinalLambda("xit", "config") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), true, this, TestType.Test)
   }

   /**
    * Finds tests in the form:
    *
    *   describe("test name").config { }
    *
    */
   private fun KtDotQualifiedExpression.tryDescribeWithConfig(): Test? {
      val name = extractLhsStringArgForDotExpressionWithRhsFinalLambda("describe", "config") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), name.text.startsWith("!"), this, TestType.Test)
   }

   /**
    * Finds tests in the form:
    *
    *   xdescribe("test name").config { }
    *
    */
   private fun KtDotQualifiedExpression.tryXDescribeWithConfig(): Test? {
      val name = extractLhsStringArgForDotExpressionWithRhsFinalLambda("xdescribe", "config") ?: return null
      return buildTest(TestName(null, name.text, name.interpolated), true, this, TestType.Test)
   }

   private fun buildTest(testName: TestName, xdisabled: Boolean, element: PsiElement, testType: TestType): Test {
      return Test(testName, locateParent(element), testType, xdisabled, element)
   }

   override fun test(element: PsiElement): Test? {
      return when (element) {
         is KtCallExpression -> element.tryIt() ?: element.tryXIt() ?: element.tryDescribe() ?: element.tryXDescribe()
         ?: element.tryContext() ?: element.tryXContent()
         is KtDotQualifiedExpression ->
            element.tryDescribeWithConfig()
               ?: element.tryXDescribeWithConfig()
               ?: element.tryItWithConfig()
               ?: element.tryXItWithConfig()
         else -> null
      }
   }

   override fun possibleLeafElements(): Set<String> {
      return setOf("OPEN_QUOTE", "DOT")
   }

   override fun test(element: LeafPsiElement): Test? {
      val call = element.ifOpenQuoteOfFunctionName(fnNames)
      if (call != null) return test(call)

      val dot = element.ifDotExpressionSeparator()
      if (dot != null) return test(dot)

      return null
   }
}
