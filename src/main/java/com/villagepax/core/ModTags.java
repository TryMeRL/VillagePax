package com.villagepax.core;

import com.villagepax.VillagePax;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ModTags {

    /**
     * Блоки, которые билдер вносит последними, когда коробка здания готова.
     * <p>
     * Отделять обстановку от несущего предикатом по блокстейту
     * ({@code isSolid}, {@code isOpaque}) — гадание о внутренностях ванили:
     * у ступеней и плит эти флаги не те, которых ждёшь, и меняются между
     * версиями молча. Тег снимает вопрос: поведение задано данными, автор
     * датапака правит его без Java, и это тот же принцип, на котором стоит
     * весь мод.
     */
    public static final TagKey<Block> BUILD_DECOR = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "build_decor"));

    /**
     * Посев: его билдер сеет самым последним, в готовое поле под зажжёнными
     * лампами.
     * <p>
     * Посев живёт светом: в темноте грядка при первом толчке соседа
     * осыпается на пол. Поставленная вместе со стенами, морковь гномьего
     * поля ложилась раньше ламп — и осыпалась вся. Тегом, как и обстановка:
     * народ из датапака сеет своё, и билдер узнает его посев по тегу.
     */
    public static final TagKey<Block> BUILD_SOWING = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "build_sowing"));

    /**
     * Сердца праздника: вокруг них водят хоровод и от них бьёт фейерверк.
     * <p>
     * Тегом, а не списком в коде: у каждого народа сердце своё, и народ,
     * добавленный датапаком, приносит своё сердце — ярмарка узнает его
     * по тегу так же, как узнаёт майское дерево.
     */
    public static final TagKey<Block> FESTIVAL_HEARTS = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "festival_hearts"));

    /**
     * Что житель считает едой.
     * <p>
     * Тег, а не {@code Item.isFood()}: последний вернёт истину и для гнилой
     * плоти, и для золотого яблока. То же решение, что и у
     * {@link #BUILD_DECOR} — поведение задают данные, и у культуры сможет
     * быть свой стол.
     */
    public static final TagKey<Item> CITIZEN_FOOD = TagKey.of(
            RegistryKeys.ITEM, new Identifier(VillagePax.MOD_ID, "citizen_food"));

    /**
     * Что вешают на бельевую верёвку.
     * <p>
     * Тегом, а не списком в коде: сукно ткача висит на ней по тому же
     * тегу, что шерсть и кожа, и верёвка не знает о нём ничего. Тем же
     * тегом чужой датапак повесит на неё своё.
     */
    public static final TagKey<Item> HANGABLE = TagKey.of(
            RegistryKeys.ITEM, new Identifier(VillagePax.MOD_ID, "hangable"));

    /**
     * Что гость трогает на земле чужой колонии.
     * <p>
     * Двери, калитки, кнопки, колокол, пульт ратуши — то, чем ходят
     * и говорят, а не то, чем строят и берут. Тегом, чтобы датапак
     * сервера мог открыть гостям свои блоки: лавку, почтовый ящик,
     * верстак на площади.
     */
    public static final TagKey<Block> GUEST_USABLE = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "guest_usable"));

    /**
     * По чему жителю приятнее идти.
     * <p>
     * Ванильный поиск пути не различает траву и мостовую, поэтому все
     * работники колонии идут одной линией напрямик и толкаются. С этим тегом
     * шаг вне дороги стоит чуть дороже, и путь сам ложится на мощёную улицу —
     * а какие блоки считать улицей, решает датапак.
     */
    public static final TagKey<Block> PREFERRED_PATH = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "preferred_path"));

    /**
     * Земля, которую билдер вправе замостить.
     * <p>
     * Улицу нельзя прокладывать по чему попало: если игрок выложил свою
     * дорожку из кварца или поставил на пути грядку, трогать это нельзя —
     * то же правило, по которому фермер не считает своими чужие посадки.
     * В теге поэтому только <b>натуральный грунт</b>: трава, земля, песок,
     * гравий. Пашня в {@code #minecraft:dirt} не входит, и улица не съест
     * поле.
     * <p>
     * Песок перечислен поимённо, а не тегом {@code #minecraft:sand}: в том
     * теге лежит и подозрительный песок, а в нём — находки археологии.
     * Замостить его значило бы уничтожить добычу молча.
     */
    public static final TagKey<Block> PAVABLE = TagKey.of(
            RegistryKeys.BLOCK, new Identifier(VillagePax.MOD_ID, "pavable"));

    private ModTags() {
    }
}
