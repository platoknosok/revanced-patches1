package app.revanced.patches.youtube.player.suggestedvideooverlay

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.youtube.player.suggestedvideooverlay.fingerprints.CoreContainerBuilderFingerprint
import app.revanced.patches.youtube.player.suggestedvideooverlay.fingerprints.TouchAreaOnClickListenerFingerprint
import app.revanced.patches.youtube.utils.integrations.Constants.PLAYER
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch
import app.revanced.patches.youtube.utils.resourceid.SharedResourceIdPatch.CoreContainer
import app.revanced.patches.youtube.utils.settings.SettingsPatch
import app.revanced.util.exception
import app.revanced.util.getWideLiteralInstructionIndex
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Hide suggested video overlay",
    description = "Adds an option to hide the suggested video overlay at the end of videos.",
    dependencies = [
        SettingsPatch::class,
        SharedResourceIdPatch::class
    ],
    compatiblePackages = [
        CompatiblePackage(
            "com.google.android.youtube",
            [
                "18.25.40",
                "18.27.36",
                "18.29.38",
                "18.30.37",
                "18.31.40",
                "18.32.39",
                "18.33.40",
                "18.34.38",
                "18.35.36",
                "18.36.39",
                "18.37.36",
                "18.38.44",
                "18.39.41",
                "18.40.34",
                "18.41.39",
                "18.42.41",
                "18.43.45",
                "18.44.41",
                "18.45.43",
                "18.46.45",
                "18.48.39",
                "18.49.37",
                "19.01.34",
                "19.02.39"
            ]
        )
    ]
)
@Suppress("unused")
object SuggestedVideoOverlayPatch : BytecodePatch(
    setOf(
        CoreContainerBuilderFingerprint,
        TouchAreaOnClickListenerFingerprint
    )
) {
    override fun execute(context: BytecodeContext) {

        CoreContainerBuilderFingerprint.result?.let {
            it.mutableMethod.apply {
                val targetIndex = getWideLiteralInstructionIndex(CoreContainer) + 4
                val targetReference =
                    getInstruction<ReferenceInstruction>(targetIndex).reference

                if (!targetReference.toString().endsWith("Landroid/view/ViewGroup;"))
                    throw PatchException("Reference did not match: $targetReference")

                val targetRegister =
                    getInstruction<TwoRegisterInstruction>(targetIndex).registerA

                addInstruction(
                    targetIndex,
                    "invoke-static {v$targetRegister}, $PLAYER->hideSuggestedVideoOverlay(Landroid/view/ViewGroup;)V"
                )
            }
        } ?: throw CoreContainerBuilderFingerprint.exception

        TouchAreaOnClickListenerFingerprint.result?.let {
            it.mutableMethod.apply {
                val insertMethod = it.mutableClass.methods.find { method -> method.parameters == listOf("Landroid/view/View${'$'}OnClickListener;") }

                insertMethod?.apply {
                    val setOnClickListenerIndex = getOnClickListenerIndex()
                    val setOnClickListenerRegister = getInstruction<FiveRegisterInstruction>(setOnClickListenerIndex).registerC

                    addInstruction(
                        setOnClickListenerIndex + 1,
                        "invoke-static {v$setOnClickListenerRegister}, $PLAYER->hideSuggestedVideoOverlayAutoPlay(Landroid/view/View;)V"
                    )
                } ?: throw PatchException("Failed to find setOnClickListener method")
            }
        } ?: throw TouchAreaOnClickListenerFingerprint.exception

        /**
         * Add settings
         */
        SettingsPatch.addPreference(
            arrayOf(
                "PREFERENCE: PLAYER_SETTINGS",
                "SETTINGS: PLAYER_EXPERIMENTAL_FLAGS",
                "SETTINGS: HIDE_SUGGESTED_VIDEO_OVERLAY"
            )
        )

        SettingsPatch.updatePatchStatus("Hide suggested video overlay")

    }

    private fun MutableMethod.getOnClickListenerIndex(): Int {
        return implementation!!.instructions.indexOfFirst { instruction ->
            if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@indexOfFirst false

            return@indexOfFirst ((instruction as Instruction35c).reference as MethodReference).name == "setOnClickListener"
        }
    }
}