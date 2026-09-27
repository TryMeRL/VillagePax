package com.villagepax.sim.festival;

import java.util.UUID;

/**
 * Участник состязания: игрок или житель-соперник.
 *
 * @param id     опознаватель игрока или жителя
 * @param player игрок ли это: призы получают только игроки, места занимают все
 * @param name   как его назвать в итогах и на кубке
 */
public record Contestant(UUID id, boolean player, String name) {
}
