// Split from TestHelpers.kt — sealed-class reflection assertions
package test.helpers

import kotlin.reflect.KClass
import kotlin.test.*

/** Return simple names of all sealed subclasses for the given KClass (filters nulls). */
fun sealedSubclassNamesOf(kclass: KClass<*>): Set<String> =
    kclass.sealedSubclasses.mapNotNull { it.simpleName }.toSet()

/** Assert that the given expected names are present among the sealed subclasses. */
fun assertSealedSubclassNamesContain(kclass: KClass<*>, expected: Set<String>, message: String = "") {
    val actual = sealedSubclassNamesOf(kclass)
    val missing = expected - actual
    assertTrue(missing.isEmpty(), message.ifBlank { "Missing ${kclass.simpleName} subclasses: $missing; actual=$actual" })
}

/** Return sealed-subclass names present that are not in the allowed set (extra items). */
fun sealedSubclassesExtra(kclass: KClass<*>, allowed: Set<String>): Set<String> =
    sealedSubclassNamesOf(kclass) - allowed

/** Assert that a specific sealed-subclass by simple name exists. */
fun assertSealedSubclassExists(kclass: KClass<*>, name: String, message: String = "") {
    val ok = sealedSubclassNamesOf(kclass).contains(name)
    assertTrue(ok, message.ifBlank { "Expected ${kclass.simpleName} to contain subclass $name" })
}

/** Return names of sealed subclasses that do NOT declare the given Java field name. */
fun sealedSubclassesMissingField(kclass: KClass<*>, fieldName: String): Set<String> =
    kclass.sealedSubclasses.filter { sub -> sub.java.declaredFields.none { it.name == fieldName } }
        .mapNotNull { it.simpleName }.toSet()

/** Assert that all sealed subclasses declare the given Java field name. */
fun assertAllSealedSubclassesHaveField(kclass: KClass<*>, fieldName: String, message: String = "") {
    val missing = sealedSubclassesMissingField(kclass, fieldName)
    assertTrue(missing.isEmpty(), message.ifBlank { "Sealed subclasses of ${kclass.simpleName} missing field '$fieldName': $missing" })
}

/** Assert that all sealed subclasses declare the required Java field names. */
fun assertSealedSubclassesHaveFields(kclass: KClass<*>, requiredFields: Set<String>, message: String = "") {
    val bad = kclass.sealedSubclasses.mapNotNull { sub ->
        val fieldNames = sub.java.declaredFields.map { it.name }.toSet()
        val missing = requiredFields - fieldNames
        if (missing.isNotEmpty()) "${sub.simpleName}: missing=$missing" else null
    }
    assertTrue(bad.isEmpty(), message.ifBlank { "Sealed subclasses of ${kclass.simpleName} missing required fields: $bad" })
}
