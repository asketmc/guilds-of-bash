package test

import core.*
import core.serde.serializeEvents
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.reflect.KClass

@P1
class SerializationTest {

    @Test
    fun `serializeEvents covers all event branches and stays compact`() {
        val base = listOf(
            DayStarted::class.java,
            InboxGenerated::class.java,
            HeroesArrived::class.java,
            ContractPosted::class.java,
            ContractTaken::class.java,
            WipAdvanced::class.java,
            ContractResolved::class.java,
            ReturnClosed::class.java,
            TrophySold::class.java,
            StabilityUpdated::class.java,
            DayEnded::class.java,
            CommandRejected::class.java,
            InvariantViolated::class.java,
            HeroDeclined::class.java,
            TrophyTheftSuspected::class.java,
            TaxDue::class.java,
            TaxPaid::class.java,
            TaxMissed::class.java,
            GuildShutdown::class.java,
            GuildRankUp::class.java,
            ProofPolicyChanged::class.java,
            ContractDraftCreated::class.java,
            ContractTermsUpdated::class.java,
            ContractCancelled::class.java
        ).map { EventFactory.create(it) as Event }

        // Extra variants to hit nullable + boolean + empty-array branches.
        val events = buildList {
            addAll(base)
            add(EventFactory.create(TaxPaid::class.java, Variant.BOOL_FALSE) as Event)
            add(EventFactory.create(ContractTermsUpdated::class.java, Variant.NULLABLES_NULL) as Event)
            add(EventFactory.create(HeroesArrived::class.java, Variant.EMPTY_ARRAYS) as Event)
        }

        val json = serializeEvents(events)

        assertTrue(json.startsWith("["), "Must start with [")
        assertTrue(json.endsWith("]"), "Must end with ]")
        assertFalse(json.contains("\n"), "Must be compact (no newlines)")
        assertFalse(json.contains("  "), "Must be compact (no indentation)")

        // Sanity: every known discriminator should appear at least once.
        val expectedTypes = listOf(
            "DayStarted",
            "InboxGenerated",
            "HeroesArrived",
            "ContractPosted",
            "ContractTaken",
            "WipAdvanced",
            "ContractResolved",
            "ReturnClosed",
            "TrophySold",
            "StabilityUpdated",
            "DayEnded",
            "CommandRejected",
            "InvariantViolated",
            "HeroDeclined",
            "TrophyTheftSuspected",
            "TaxDue",
            "TaxPaid",
            "TaxMissed",
            "GuildShutdown",
            "GuildRankUp",
            "ProofPolicyChanged",
            "ContractDraftCreated",
            "ContractTermsUpdated",
            "ContractCancelled"
        )
        expectedTypes.forEach { t ->
            assertTrue(json.contains("\"type\":\"$t\""), "Missing serialized type=$t")
        }
    }

    @Test
    fun `common fields order is stable and type is first`() {
        val e = EventFactory.create(DayStarted::class.java) as Event
        val json = serializeEvents(listOf(e))
        val obj = json.removePrefix("[").removeSuffix("]")

        val iType = obj.indexOf("\"type\"")
        val iDay = obj.indexOf("\"day\"")
        val iRevision = obj.indexOf("\"revision\"")
        val iCmdId = obj.indexOf("\"cmdId\"")
        val iSeq = obj.indexOf("\"seq\"")

        assertTrue(iType >= 0 && iDay >= 0 && iRevision >= 0 && iCmdId >= 0 && iSeq >= 0, "Missing common keys")
        assertTrue(iType < iDay && iDay < iRevision && iRevision < iCmdId && iCmdId < iSeq, "Common field order must be fixed")
    }

    @Test
    fun `string fields are JSON-escaped`() {
        val e = EventFactory.create(GuildShutdown::class.java) as Event
        val json = serializeEvents(listOf(e))

        // RAW_ESCAPE_STRING gets injected into all String constructor params by EventFactory.
        val expectedEscaped = "a\\\\b\\\"c\\nd\\re\\tf"
        assertTrue(json.contains("\"reason\":\"$expectedEscaped\""), "Expected escaped reason")
        assertFalse(json.contains(RAW_ESCAPE_STRING), "Raw control chars must not appear in JSON output")
    }

    @Test
    fun `nullable fields emit null for ContractTermsUpdated`() {
        val eNulls = EventFactory.create(ContractTermsUpdated::class.java, Variant.NULLABLES_NULL) as Event
        val json = serializeEvents(listOf(eNulls))

        assertTrue(json.contains("\"oldFee\":null"), "oldFee must emit null")
        assertTrue(json.contains("\"newFee\":null"), "newFee must emit null")
        assertTrue(json.contains("\"oldSalvage\":null"), "oldSalvage must emit null")
        assertTrue(json.contains("\"newSalvage\":null"), "newSalvage must emit null")
    }

    @Test
    fun `TaxPaid boolean is emitted as true or false without quotes`() {
        val eTrue = EventFactory.create(TaxPaid::class.java, Variant.DEFAULT) as Event
        val eFalse = EventFactory.create(TaxPaid::class.java, Variant.BOOL_FALSE) as Event

        val json = serializeEvents(listOf(eTrue, eFalse))

        assertTrue(json.contains("\"isPartialPayment\":true"), "Must contain true boolean")
        assertTrue(json.contains("\"isPartialPayment\":false"), "Must contain false boolean")
        assertFalse(json.contains("\"isPartialPayment\":\"true\""), "Boolean must not be quoted")
        assertFalse(json.contains("\"isPartialPayment\":\"false\""), "Boolean must not be quoted")
    }

    @Test
    fun `IntArray fields support empty and non-empty without whitespace`() {
        val nonEmpty = EventFactory.create(HeroesArrived::class.java, Variant.DEFAULT) as Event
        val empty = EventFactory.create(HeroesArrived::class.java, Variant.EMPTY_ARRAYS) as Event

        val json = serializeEvents(listOf(nonEmpty, empty))

        assertTrue(json.contains("\"heroIds\":[1,2]"), "Non-empty array must be compact")
        assertTrue(json.contains("\"heroIds\":[]"), "Empty array must be emitted as []")
        assertFalse(json.contains(" "), "No insignificant whitespace expected")
    }
}

private enum class Variant {
    DEFAULT,
    BOOL_FALSE,
    NULLABLES_NULL,
    EMPTY_ARRAYS
}

private const val RAW_ESCAPE_STRING: String = "a\\b\"c\nd\re\tf"

private object EventFactory {

    fun <T : Any> create(clazz: Class<T>, variant: Variant = Variant.DEFAULT): T {
        val ctors = clazz.declaredConstructors.sortedByDescending { it.parameterCount }
        var last: Throwable? = null

        for (ctor in ctors) {
            try {
                ctor.isAccessible = true
                val args = buildArgs(clazz, ctor.parameterTypes, ctor.parameters, variant, depth = 0)
                @Suppress("UNCHECKED_CAST")
                return ctor.newInstance(*args) as T
            } catch (t: Throwable) {
                last = t
            }
        }

        throw AssertionError(
            "Unable to instantiate ${clazz.name}; constructors tried=${ctors.size}; lastError=${last?.javaClass?.name}: ${last?.message}",
            last
        )
    }

    private fun buildArgs(
        owningClass: Class<*>,
        paramTypes: Array<Class<*>>,
        params: Array<java.lang.reflect.Parameter>,
        variant: Variant,
        depth: Int
    ): Array<Any?> {
        if (depth > 6) {
            return arrayOfNulls(paramTypes.size)
        }

        val args = arrayOfNulls<Any?>(paramTypes.size)
        // Detect Kotlin-side nullability for the constructor parameters.
        val kotlinNullables = detectKotlinNullablesForJavaConstructor(owningClass, paramTypes)

        for (i in paramTypes.indices) {
            val type = paramTypes[i]
            val nullable = kotlinNullables.getOrElse(i) { false }
            args[i] = valueFor(type, variant, nullable, depth + 1)
        }

        // Fix known "count + IntArray" invariants for the two event types that declare them (per serializer contract).
        if (owningClass.simpleName == "InboxGenerated" || owningClass.simpleName == "HeroesArrived") {
            for (arrIdx in paramTypes.indices) {
                if (isIntArrayType(paramTypes[arrIdx])) {
                    val arr = args[arrIdx] as? IntArray ?: continue
                    for (j in (arrIdx - 1) downTo 0) {
                        if (paramTypes[j] == Int::class.javaPrimitiveType) {
                            args[j] = arr.size
                            break
                        }
                    }
                }
            }
        }

        return args
    }

    private fun detectKotlinNullablesForJavaConstructor(owningClass: Class<*>, paramTypes: Array<Class<*>>): BooleanArray {
        try {
            val kclass = owningClass.kotlin
            // Find candidate Kotlin constructors with the same arity and matching parameter types (best-effort).
            val candidates = kclass.constructors.filter { it.parameters.size == paramTypes.size }
            for (kctor in candidates) {
                var match = true
                for (i in kctor.parameters.indices) {
                    val kparamClassifier = kctor.parameters[i].type.classifier as? KClass<*>
                    if (kparamClassifier == null) { match = false; break }
                    // Compare Kotlin classifier with Java parameter's kotlin representation.
                    if (kparamClassifier != paramTypes[i].kotlin) {
                        match = false
                        break
                    }
                }
                if (match) {
                    return BooleanArray(kctor.parameters.size) { idx -> kctor.parameters[idx].type.isMarkedNullable }
                }
            }
        } catch (_: Throwable) {
            // reflection may fail; fall back to conservative non-null assumption
        }
        return BooleanArray(paramTypes.size) { false }
    }

    private fun valueFor(type: Class<*>, variant: Variant, nullable: Boolean, depth: Int): Any? {
        if (nullable && variant == Variant.NULLABLES_NULL) return null

        // Primitives / boxed
        if (type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType) return 100 + depth
        if (type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType) return 10_000L + depth
        if (type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType) {
            return when (variant) {
                Variant.BOOL_FALSE -> false
                else -> true
            }
        }
        if (type == String::class.java) return RAW_ESCAPE_STRING

        // int[]
        if (isIntArrayType(type)) {
            return when (variant) {
                Variant.EMPTY_ARRAYS -> intArrayOf()
                else -> intArrayOf(1, 2)
            }
        }

        // java.util.List (not expected for current events, but safe)
        if (java.util.List::class.java.isAssignableFrom(type)) {
            return emptyList<Any?>()
        }

        // Enums
        if (type.isEnum) {
            val constants = type.enumConstants
            if (!constants.isNullOrEmpty()) return constants[0]
        }

        // Nested DTO/snapshot-like objects
        val ctors = type.declaredConstructors.sortedByDescending { it.parameterCount }
        for (ctor in ctors) {
            try {
                ctor.isAccessible = true
                val nestedArgs = buildArgs(type, ctor.parameterTypes, ctor.parameters, Variant.DEFAULT, depth)
                return ctor.newInstance(*nestedArgs)
            } catch (_: Throwable) {
                // try next constructor
            }
        }

        // Last resort: null for reference types (will fail fast if not allowed by Kotlin null-checks).
        return null
    }

    private fun isIntArrayType(type: Class<*>): Boolean {
        return type.isArray && type.componentType == Int::class.javaPrimitiveType
    }
}
