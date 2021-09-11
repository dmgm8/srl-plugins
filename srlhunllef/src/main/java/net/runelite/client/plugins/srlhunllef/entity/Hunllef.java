/*
 * Copyright (c) 2020, dutta64 <https://github.com/dutta64>
 * Copyright (c) 2019, ganom <https://github.com/Ganom>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.runelite.client.plugins.srlhunllef.entity;

import java.awt.Color;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;

public class Hunllef
{
	private static final int ATTACK_TICK_SPEED = 6;

	private static final int MAX_ATTACK_COUNT = 4;
	private static final int MAX_PLAYER_ATTACK_COUNT = 6;

	@Getter
	private final NPC npc;

	@Getter
	private AttackPhase attackPhase;

	@Getter
	private int attackCount;
	@Getter
	private int playerAttackCount;
	@Getter
	private int ticksUntilNextAttack;

	public Hunllef(final NPC npc)
	{
		this.npc = npc;

		this.attackCount = MAX_ATTACK_COUNT;
		this.playerAttackCount = MAX_PLAYER_ATTACK_COUNT;
		this.ticksUntilNextAttack = 0;

		this.attackPhase = AttackPhase.RANGE;
	}

	public void decrementTicksUntilNextAttack()
	{
		if (ticksUntilNextAttack > 0)
		{
			ticksUntilNextAttack--;
		}
	}

	public void updatePlayerAttackCount()
	{
		if (--playerAttackCount <= 0)
		{
			playerAttackCount = MAX_PLAYER_ATTACK_COUNT;
		}
	}

	public void updateAttackCount()
	{
		ticksUntilNextAttack = ATTACK_TICK_SPEED;

		if (--attackCount <= 0)
		{
			attackPhase = attackPhase == AttackPhase.RANGE ? AttackPhase.MAGIC : AttackPhase.RANGE;
			attackCount = MAX_ATTACK_COUNT;
		}
	}

	@AllArgsConstructor
	@Getter
	public enum AttackPhase
	{
		MAGIC(Color.CYAN, Prayer.PROTECT_FROM_MAGIC),
		RANGE(Color.GREEN, Prayer.PROTECT_FROM_MISSILES);

		private final Color color;
		private final Prayer prayer;
	}
}