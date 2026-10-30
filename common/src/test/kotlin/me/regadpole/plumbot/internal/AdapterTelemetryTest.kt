/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.internal

import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 适配器类别上报：只上报**实际在跑的**适配器类型，且不含任何标识信息（上报面越小越好）。
 */
class AdapterTelemetryTest {

    @AfterTest
    fun cleanup() {
        BotProvider.unloadBot()
        BotProvider.clearFactories()
    }

    @Test
    fun `reports unknown while no adapter is loaded`() {
        assertEquals("unknown", AdapterTelemetry.current())
    }

    @Test
    fun `reports the loaded adapter type in lower case`() {
        val context = FakePlatformContext()
        val bot = FakeBot(context, type = "MiraiMC")
        BotProvider.registerFactory(object : BotFactory {
            override val metadata = bot.metadata
            override fun create(context: PlatformContext) = bot
        })

        BotProvider.loadBot(context, bot.metadata.type)

        assertEquals("miraimc", AdapterTelemetry.current(), "上报的是实际加载的适配器类型（小写归一）")
    }
}
