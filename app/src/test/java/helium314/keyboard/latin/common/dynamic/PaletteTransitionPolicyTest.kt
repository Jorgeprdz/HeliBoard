// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import org.junit.Assert.*
import org.junit.Test

class PaletteTransitionPolicyTest {
    private fun s(app: String?, epoch: Long, field: Int = 1, input: Int = 1, opts: Int = 0, display: Int? = 0) =
        SessionIdentity(app, field, input, opts, display, epoch)
    private fun p(seed: Int, family: String = "TONAL_SPOT", dark: Boolean = false, active: Boolean = true) =
        PaletteIdentity(seed, family, dark, "material", true, null, 1, active)
    private fun d(old: SessionIdentity?, next: SessionIdentity, oldColor: PaletteIdentity?, newColor: PaletteIdentity?, restart: Boolean = false, composing: Boolean = false, active: Long = next.epoch) =
        PaletteTransitionPolicy.decide(old, next, restart, composing, oldColor, newColor, active)

    @Test fun samePaletteNeverRecreatesEvenWithNewEpochFieldAppOrRestart() {
        val a = p(0xff123456.toInt())
        assertEquals(PaletteDecision.KeepPalette, d(s("a",1),s("a",2,8),a,a,true))
        assertEquals(PaletteDecision.KeepPalette, d(s("a",2),s("b",3),a,a,true))
        assertEquals(PaletteDecision.KeepPalette, d(s("a",1),s("b",2),a,a,false,true))
    }
    @Test fun aToBToAAndCrossPackageRestartKeepCorrectColor() {
        val a = p(0xff123456.toInt()); val b = p(0xffabcdef.toInt())
        assertEquals(PaletteDecision.ApplyAtBoundary(b),d(s("a",1),s("b",2,input=1),a,b,true))
        assertEquals(PaletteDecision.ApplyAtBoundary(a),d(s("b",2),s("a",3,input=1),b,a,true))
        assertEquals(PaletteDecision.RejectStale,d(s("b",4),s("a",3),b,a,false,active=4))
    }
    @Test fun composingAlwaysDefersChangedPaletteButNotEqualPalette() {
        val a=p(0xff111111.toInt());val b=p(0xff222222.toInt())
        assertEquals(PaletteDecision.Defer(b),d(s("a",1),s("b",2),a,b,true,true))
        assertEquals(PaletteDecision.KeepPalette,d(s("a",1),s("a",2),a,a,true,true))
    }
    @Test fun noCandidateIsTypedFallbackAndOldEpochIsRejectedFirst() {
        assertEquals(PaletteDecision.Fallback("NO_EFFECTIVE_PALETTE"),d(null,s(null,1),null,null))
        assertEquals(PaletteDecision.RejectStale,d(s("b",2),s("a",1),null,null,active=2))
    }
    @Test fun familyDayNightAndRenderingDetailsAllAffectPaletteIdentity() {
        val a=p(0xff33ccdd.toInt())
        val variants=listOf(a.copy(family="CMF"),a.copy(dark=true),a.copy(themeStyle="rounded"),a.copy(borders=false),a.copy(backgroundSignature="custom"),a.copy(mapperVersion=2),a.copy(byAppActive=false),a.copy(argb=0xff555555.toInt()))
        for (v in variants) assertEquals(PaletteDecision.ApplyAtBoundary(v),d(s("a",1),s("a",2),a,v))
    }
    @Test fun selectedTextAndProtectedFieldMustBeCallerControlledAndTextFree() {
        val a=p(0xff0000ff.toInt());val b=p(0xff00ff00.toInt())
        assertEquals(PaletteDecision.Defer(b),d(s("a",1,input=129),s("b",2,input=129),a,b,composing=true))
        assertEquals(PaletteDecision.ApplyAtBoundary(b),d(s("a",1,field=8),s("b",2,field=9),a,b))
    }
    @Test fun missingCandidateDuringCompositionOrSelectionKeepsFullOldPalette() {
        val a=p(0xffabcdef.toInt())
        assertEquals(PaletteDecision.KeepPalette,
            d(s("app.a",1),s("app.b",2),a,null,restart=true,composing=true))
        assertEquals(PaletteDecision.Fallback("NO_EFFECTIVE_PALETTE"),
            d(s("app.a",1),s("app.b",2),a,null,restart=true,composing=false))
    }
    @Test fun disabledModeStableAndLateWorkerCannotMutate() {
        val system=p(0xff6750a4.toInt(),active=false)
        assertEquals(PaletteDecision.KeepPalette,d(s("a",1),s("b",2),system,system))
        assertEquals(PaletteDecision.RejectStale,d(s("b",2),s("a",1),system,p(0xff0088ff.toInt()),active=2))
    }
}
