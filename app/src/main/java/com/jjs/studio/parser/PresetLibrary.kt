package com.jjs.studio.parser

import com.jjs.studio.model.JjsNode
import com.jjs.studio.model.JjsSkill
import com.jjs.studio.model.NodeKind

object PresetLibrary {

    fun getSampleSkills(): List<JjsSkill> {
        return listOf(
            createHollowPurplePreset(),
            createMalevolentShrinePreset(),
            createBlackFlashPreset(),
            createFlameArrowPreset(),
            createWorldSlashPreset()
        )
    }

    fun createHollowPurplePreset(): JjsSkill {
        val skill = JjsSkill(
            name = "Hollow_Purple",
            key = 1,
            duration = 3.5,
            position = "0, 2, 5"
        )
        val defaultNodes = mutableListOf(
            JjsNode(
                kind = NodeKind.SFX,
                name = "Charge SFX",
                soundId = "9114384024",
                volume = 1.0,
                playbackSpeed = 1.0,
                time = 0.0
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Blue Compression",
                texture = "5859188448",
                color = "50, 120, 255, 30, 80, 240",
                size = "2.5, 0.4",
                speed = 25.0,
                rate = 45.0,
                lifetime = 0.8,
                spreadAngle = "360.00, 360.00, 360.00",
                time = 0.05
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Red Reversal",
                texture = "5859188448",
                color = "255, 40, 70, 240, 20, 50",
                size = "2.2, 0.4",
                speed = 22.0,
                rate = 40.0,
                lifetime = 0.8,
                spreadAngle = "360.00, 360.00, 360.00",
                time = 0.15
            ),
            JjsNode(
                kind = NodeKind.WAIT,
                name = "Merge Pause",
                time = 0.4
            ),
            JjsNode(
                kind = NodeKind.VISUAL_MESH,
                name = "Purple Core Sphere",
                meshId = "6023773194",
                visualSize = "6, 6, 6",
                color = "190, 40, 255",
                altColor = "255, 255, 255",
                position = "0, 0, -2",
                time = 0.6
            ),
            JjsNode(
                kind = NodeKind.VISUAL_CAMERA,
                name = "Shake Cam",
                fov = 60.0,
                time = 0.65
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Singularity Annihilation",
                texture = "7052988630",
                color = "210, 60, 255, 255, 255, 255",
                size = "5.0, 1.2",
                speed = 50.0,
                rate = 80.0,
                lifetime = 1.4,
                spreadAngle = "45.00, 45.00, 0.00",
                time = 0.7
            ),
            JjsNode(
                kind = NodeKind.SFX,
                name = "Impact Blast",
                soundId = "9114387890",
                volume = 1.2,
                playbackSpeed = 0.9,
                time = 0.7
            ),
            JjsNode(
                kind = NodeKind.TAG,
                name = "Obliterate Tag",
                tagLabel = "Knockback_Super",
                time = 0.8
            )
        )
        skill.branches["Default"] = defaultNodes
        return skill
    }

    fun createMalevolentShrinePreset(): JjsSkill {
        val skill = JjsSkill(
            name = "Malevolent_Dismantle",
            key = 2,
            duration = 2.8,
            position = "0, 0, 0"
        )
        val defaultNodes = mutableListOf(
            JjsNode(
                kind = NodeKind.SFX,
                name = "Blade Unsheathe",
                soundId = "9114381122",
                volume = 0.9,
                time = 0.0
            ),
            JjsNode(
                kind = NodeKind.VISUAL_MESH,
                name = "Dismantle Slash Crescent",
                meshId = "4815162342",
                visualSize = "8, 0.2, 3",
                color = "240, 240, 240",
                time = 0.1
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Blood Spray",
                texture = "5859188448",
                color = "180, 15, 25, 120, 5, 10",
                size = "1.8, 0.2",
                speed = 30.0,
                rate = 60.0,
                time = 0.2
            ),
            JjsNode(
                kind = NodeKind.CONNECT,
                name = "Hit Connect",
                signal = "Hit",
                range = "15",
                time = 0.25
            ),
            JjsNode(
                kind = NodeKind.BRANCH,
                name = "Cleave Trigger",
                targetBranch = "Cleave_Followup",
                time = 0.3
            )
        )

        val cleaveNodes = mutableListOf(
            JjsNode(
                kind = NodeKind.SFX,
                name = "Cleave Tear SFX",
                soundId = "9114384455",
                volume = 1.1,
                time = 0.0
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Grid Dismantle Sparks",
                texture = "5859188448",
                color = "255, 230, 140, 255, 80, 30",
                size = "2.0, 0.1",
                speed = 40.0,
                rate = 90.0,
                time = 0.05
            ),
            JjsNode(
                kind = NodeKind.TAG,
                name = "Execute Stun",
                tagLabel = "Dismantle_Cut",
                time = 0.1
            )
        )

        skill.branches["Default"] = defaultNodes
        skill.branches["Cleave_Followup"] = cleaveNodes
        return skill
    }

    fun createBlackFlashPreset(): JjsSkill {
        val skill = JjsSkill(
            name = "Black_Flash_Impact",
            key = 3,
            duration = 1.8,
            position = "0, 1.5, 0"
        )
        val defaultNodes = mutableListOf(
            JjsNode(
                kind = NodeKind.WAIT,
                name = "Kinetic Windup",
                time = 0.15
            ),
            JjsNode(
                kind = NodeKind.VISUAL_CAMERA,
                name = "Impact Zoom",
                fov = 45.0,
                time = 0.2
            ),
            JjsNode(
                kind = NodeKind.SFX,
                name = "Heavy Bass Snap",
                soundId = "9114389900",
                volume = 1.3,
                playbackSpeed = 0.95,
                time = 0.25
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Crimson Spark Distortion",
                texture = "7052988630",
                color = "255, 20, 40, 20, 20, 25",
                size = "4.0, 0.5",
                speed = 60.0,
                rate = 120.0,
                lifetime = 0.6,
                spreadAngle = "360.00, 360.00, 360.00",
                time = 0.25
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Black Lightning Tendrils",
                texture = "5859188448",
                color = "15, 15, 20, 230, 40, 50",
                size = "3.2, 0.2",
                speed = 45.0,
                rate = 75.0,
                time = 0.3
            ),
            JjsNode(
                kind = NodeKind.TAG,
                name = "Hyperarmor Tag",
                tagLabel = "Stun_Super",
                time = 0.3
            )
        )
        skill.branches["Default"] = defaultNodes
        return skill
    }

    fun createFlameArrowPreset(): JjsSkill {
        val skill = JjsSkill(
            name = "Flame_Arrow_Fuga",
            key = 4,
            duration = 3.0,
            position = "0, 2, 2"
        )
        val nodes = mutableListOf(
            JjsNode(
                kind = NodeKind.SFX,
                name = "Fire Ignite",
                soundId = "9114382233",
                volume = 1.0,
                time = 0.0
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Flame Bow Gathering",
                texture = "5859188448",
                color = "255, 120, 20, 255, 220, 40",
                size = "3.0, 0.6",
                speed = 20.0,
                rate = 50.0,
                time = 0.1
            ),
            JjsNode(
                kind = NodeKind.WAIT,
                name = "Release Delay",
                time = 0.5
            ),
            JjsNode(
                kind = NodeKind.SFX,
                name = "Arrow Blast Whoosh",
                soundId = "9114386677",
                volume = 1.2,
                time = 0.6
            ),
            JjsNode(
                kind = NodeKind.VISUAL_MESH,
                name = "Fire Arrow Mesh",
                meshId = "7891234567",
                visualSize = "1, 1, 6",
                color = "255, 180, 50",
                time = 0.6
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Incineration Field",
                texture = "7052988630",
                color = "255, 60, 10, 255, 200, 30",
                size = "8.0, 2.0",
                speed = 35.0,
                rate = 100.0,
                time = 0.8
            )
        )
        skill.branches["Default"] = nodes
        return skill
    }

    fun createWorldSlashPreset(): JjsSkill {
        val skill = JjsSkill(
            name = "World_Cutting_Slash",
            key = 5,
            duration = 2.0,
            position = "0, 1, 0"
        )
        val nodes = mutableListOf(
            JjsNode(
                kind = NodeKind.SFX,
                name = "Space Tear Audio",
                soundId = "9114385566",
                volume = 1.1,
                time = 0.0
            ),
            JjsNode(
                kind = NodeKind.PARTICLE,
                name = "Distortion Rip",
                texture = "5859188448",
                color = "255, 255, 255, 30, 30, 40",
                size = "12.0, 0.1",
                speed = 10.0,
                rate = 30.0,
                time = 0.1
            ),
            JjsNode(
                kind = NodeKind.CONNECT,
                name = "Global Sever Connect",
                signal = "Hit",
                range = "inf",
                time = 0.15
            )
        )
        skill.branches["Default"] = nodes
        return skill
    }
}
