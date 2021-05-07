// IS_APPLICABLE: false
// WITH_RUNTIME
// ERROR: Unresolved reference: unresolved
// IGNORE_FIR

import java.util.regex.Pattern

fun foo() {
    Pattern.<caret>unresolved()
}
