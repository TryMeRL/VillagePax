#!/usr/bin/env python3
"""Поручения по народам: таблица ErrandBook.java и слова к ней в обоих словарях.

Запуск из корня: python3 tools/make-errands.py
"""
import collections
import json

# culture -> trade -> [(item, count, ru, en), ...]
E = {
"norman": {
 "elder": [("BREAD", 8, "К ярмарке на Святого Мартина надо напечь хлеба на всех, а пекарня не поспевает.", "The Saint Martin fair is near, and the bakery cannot bake for everyone in time."),
           ("CANDLE", 6, "В часовне догорают свечи, а к вечерне их должно быть шесть.", "The chapel candles are burning low, and vespers needs six fresh ones.")],
 "lumberjack": [("OAK_LOG", 16, "Сеньор велел подновить частокол, а дубовых брёвен не хватает.", "The lord wants the palisade mended, and we are short of oak logs."),
                ("IRON_AXE", 1, "Топор сломался о сучковатый вяз. Новый нужен до заморозков.", "My axe broke on a knotty elm. I need a new one before the frost.")],
 "farmer": [("WHEAT_SEEDS", 16, "Озимые сеять пора, а семенное зерно поели мыши.", "It is time to sow the winter wheat, and the mice ate the seed grain."),
            ("APPLE", 8, "Хочу заложить яблоневый сад, как дома, в Нормандии. Принеси яблок на семена.", "I want to plant an orchard like the ones back home. Bring apples for the pips.")],
 "brewer": [("APPLE", 12, "Сидр сам себя не сварит: нужны яблоки, и побольше.", "Cider will not brew itself: I need apples, and plenty."),
            ("SUGAR", 8, "Кальвадосу к празднику не хватает сахара.", "The feast calvados is short of sugar.")],
 "weaver": [("WHITE_WOOL", 8, "Аббатство заказало рясы, а шерсти нет.", "The abbey ordered habits, and there is no wool."),
            ("BLUE_DYE", 4, "Знамя сеньора выцвело. Нужна синяя краска.", "The lord's banner has faded. I need blue dye.")],
 "guard": [("ARROW", 16, "Лучники на стене стреляют, а колчаны пустеют.", "The archers on the wall keep shooting, and the quivers run empty."),
           ("SHIELD", 1, "Щит треснул в последней стычке. Без нового на пост не выйду.", "My shield split in the last skirmish. I will not stand watch without a new one.")],
 "builder": [("STONE_BRICKS", 16, "Поднимаем донжон, а тёсаного камня мало.", "We are raising a keep, and dressed stone is short."),
             ("COBBLESTONE", 32, "Дорогу к церкви мостить нечем.", "There is nothing to pave the church road with.")],
 "courier": [("LEATHER", 4, "Сумка порвалась на дороге в Руан. Нужна кожа на заплату.", "My satchel tore on the Rouen road. I need leather to patch it."),
             ("PAPER", 8, "Писарь просит бумаги для грамот, а везти их мне.", "The scribe wants paper for charters, and I am the one to carry them.")],
},
"maya": {
 "elder": [("COCOA_BEANS", 12, "Совет пьёт какао на рассвете, а бобы на исходе.", "The council drinks cacao at dawn, and the beans are running out."),
           ("GOLD_INGOT", 2, "Жрецы просят золота для маски Солнца.", "The priests ask for gold for the mask of the Sun.")],
 "lumberjack": [("JUNGLE_LOG", 16, "Леса у храма гниют от дождей. Нужно свежее дерево джунглей.", "The temple scaffolds rot in the rains. I need fresh jungle wood."),
                ("VINE", 8, "Лианами вяжем мост над ущельем. Принеси ещё.", "We bind the gorge bridge with vines. Bring more.")],
 "farmer": [("PUMPKIN_SEEDS", 8, "На мильпе рядом с маисом растёт тыква, а семян не хватает.", "Squash grows beside the maize on the milpa, and I am short of seeds."),
            ("BONE_MEAL", 8, "Земля устала после пала. Костная мука её разбудит.", "The land is tired after the burning. Bone meal will wake it.")],
 "brewer": [("HONEYCOMB", 4, "Балче варят на мёде диких пчёл. Нужны соты.", "Balché is brewed with wild honey. I need combs."),
            ("COCOA_BEANS", 8, "Без бобов пену на праздничном шоколаде не взбить.", "No froth on the feast chocolate without beans.")],
 "weaver": [("STRING", 16, "Поясной станок ждёт нити.", "My backstrap loom is waiting for thread."),
            ("RED_DYE", 4, "Узор ягуара ткут красным, а краски нет.", "The jaguar pattern is woven in red, and the dye is gone.")],
 "guard": [("ARROW", 16, "Дротики для атлатля кончились. Сгодятся и стрелы.", "We are out of atlatl darts. Arrows will do."),
           ("OBSIDIAN", 2, "Обсидиан на лезвия макуауитля. Без него это просто дубина.", "Obsidian for the macuahuitl blades. Without it the club is just a club.")],
 "builder": [("MOSSY_STONE_BRICKS", 12, "Ступени пирамиды стёрлись. Нужен замшелый кирпич в тон.", "The pyramid steps are worn. I need mossy bricks to match."),
             ("CLAY_BALL", 12, "Штукатурим храм известью с глиной, а глины мало.", "We plaster the temple with lime and clay, and the clay is short.")],
 "courier": [("FEATHER", 8, "Перья для гонца: по ним узнают, что весть от жрецов.", "Feathers for the runner: by them people know the news comes from the priests."),
             ("LEATHER", 3, "Сандалии стёрлись на белой дороге. Нужна кожа.", "My sandals wore through on the white road. I need leather.")],
},
"pony": {
 "elder": [("CAKE", 1, "Завтра день рождения малышки Фиалки, а без торта праздник не праздник!", "Little Violet's birthday is tomorrow, and no party is a party without a cake!"),
           ("SUNFLOWER", 4, "Ратуша украшает площадь подсолнухами к Дню дружбы.", "The town hall is decorating the square with sunflowers for Friendship Day.")],
 "lumberjack": [("BIRCH_LOG", 12, "Строим мостик через ручей. Берёза светлая и весёлая!", "We are building a little bridge over the brook. Birch is bright and cheerful!"),
                ("OAK_SAPLING", 6, "Посадим рощицу для пикников!", "Let's plant a grove for picnics!")],
 "farmer": [("CARROT", 12, "Морковный пирог на весь городок! Морковки маловато.", "Carrot pie for the whole town! Not quite enough carrots."),
            ("APPLE", 8, "Пора яблок! Бабуля варит повидло, помоги набрать ещё.", "Apple season! Granny is making jam, help gather some more.")],
 "brewer": [("SWEET_BERRIES", 12, "Ягодный лимонад для ярмарки, а ягодки кончились!", "Berry lemonade for the fair, and we are out of berries!"),
            ("SUGAR", 8, "Сладкий пунш без сахара — просто водичка.", "Sweet punch without sugar is just water.")],
 "weaver": [("PINK_WOOL", 6, "Шью платье для бала. Нужна розовая шерсть!", "I am sewing a gown for the gala. I need pink wool!"),
            ("STRING", 12, "Ленты в гриву для конкурса причёсок! Нужны нитки.", "Mane ribbons for the hairdo contest! I need string.")],
 "guard": [("TORCH", 12, "Ночью у дикого леса страшновато. Факелов бы!", "The wild woods are spooky at night. Some torches, please!"),
           ("SHIELD", 1, "Учусь быть храброй стражницей, а щита нет!", "I am training to be a brave guard, and I have no shield!")],
 "builder": [("BRICKS", 16, "Пекарне нужна новая печь из кирпича.", "The bakery needs a new brick oven."),
             ("GLASS", 8, "Витражи для ратуши, стекла нужно много!", "Stained windows for the town hall, so much glass!")],
 "courier": [("PAPER", 8, "Приглашения на вечеринку, а бумаги не хватает!", "Party invitations, and not enough paper!"),
             ("APPLE", 6, "Перекус в дорогу. Почта ждёт, а животик урчит!", "A snack for the road. The mail waits, but my tummy rumbles!")],
},
"nord": {
 "elder": [("COOKED_BEEF", 8, "Через три дня соберётся тинг, и гостей надо кормить мясом.", "The Thing gathers in three days, and the guests must be fed meat."),
           ("GOLD_INGOT", 2, "Ярл обещал скальду кольцо, а золота нет.", "The jarl promised the skald a ring, and there is no gold.")],
 "lumberjack": [("SPRUCE_LOG", 16, "Длинный дом зимует, и ели на дрова уходит много.", "The longhouse is wintering, and it burns a lot of spruce."),
                ("STICK", 32, "Вёсла для драккара строгать не из чего.", "There is nothing left to carve the longship's oars from.")],
 "farmer": [("BEETROOT_SEEDS", 12, "Лето короткое, свёкла растёт быстро. Семян бы!", "Summer is short, and beets grow fast. Seeds, please!"),
            ("WHEAT", 16, "Ячмень для хлеба и эля побило градом.", "Hail flattened the barley for bread and ale.")],
 "brewer": [("HONEY_BOTTLE", 4, "Мёд для медовухи к Йолю: бочки пустеют.", "Honey for the Yule mead: the casks are emptying."),
            ("WHEAT", 16, "Эль варят из зерна, а зерна мало.", "Ale is brewed from grain, and the grain is short.")],
 "weaver": [("WHITE_WOOL", 8, "Парус драккара прохудился. Нужна шерсть.", "The longship's sail is worn through. I need wool."),
            ("RED_DYE", 4, "Красные полосы на парусе — знак нашего рода.", "Red stripes on the sail mark our kin.")],
 "guard": [("IRON_INGOT", 3, "Кольчуга порвана. Нужно железо на кольца.", "My mail is torn. I need iron for rings."),
           ("SHIELD", 1, "Стена щитов держится на каждом щите. Мой расколот.", "A shield wall stands on every shield, and mine is split.")],
 "builder": [("SPRUCE_PLANKS", 24, "Стены длинного дома — из еловых досок.", "Longhouse walls are spruce planks."),
             ("COBBLESTONE", 32, "Нужен фундамент под новую кузню.", "The new forge needs a foundation.")],
 "courier": [("LEATHER", 4, "Сапоги для зимней тропы. Нужна кожа.", "Boots for the winter trail. I need leather."),
             ("COOKED_COD", 6, "Треска в дорогу до соседнего фьорда.", "Cod for the road to the next fjord.")],
},
"yamato": {
 "elder": [("PAPER", 8, "Святилище готовит свитки к празднику фонарей, а бумаги мало.", "The shrine is preparing scrolls for the lantern festival, and paper is short."),
           ("CHERRY_SAPLING", 2, "Сакура у святилища засохла. Посадим новую.", "The sakura by the shrine has withered. Let us plant a new one.")],
 "lumberjack": [("BAMBOO", 16, "Бамбук нужен на изгороди и водостоки.", "Bamboo is needed for fences and water pipes."),
                ("CHERRY_LOG", 8, "Вишнёвое дерево для новых ворот-тории.", "Cherry wood for a new torii gate.")],
 "farmer": [("BONE_MEAL", 8, "Рисовые чеки истощились. Их надо подкормить.", "The rice paddies are tired. They need feeding."),
            ("BEETROOT", 10, "Редька для засолки на зиму.", "Radish to pickle for the winter.")],
 "brewer": [("WHEAT", 16, "Саке ставят из отборного зерна.", "Sake is brewed from the finest grain."),
            ("GLASS_BOTTLE", 6, "Бутыли для праздничного саке.", "Bottles for the festival sake.")],
 "weaver": [("STRING", 16, "Шёлковая нить на кимоно.", "Silk thread for a kimono."),
            ("PINK_DYE", 4, "Узор сакуры на пояс-оби. Нужна розовая краска.", "A sakura pattern for an obi. I need pink dye.")],
 "guard": [("IRON_INGOT", 3, "Клинок тупится. Железо для кузнеца.", "My blade is dulling. Iron for the smith."),
           ("ARROW", 16, "Стрелы для юми: стража упражняется на рассвете.", "Arrows for the yumi: the guard trains at dawn.")],
 "builder": [("CHERRY_PLANKS", 16, "Пагода растёт, и нужны вишнёвые доски.", "The pagoda is rising, and I need cherry planks."),
             ("CLAY_BALL", 12, "Глина на черепицу для крыши.", "Clay for the roof tiles.")],
 "courier": [("PAPER", 6, "Письма даймё, а бумага кончилась.", "Letters for the daimyo, and the paper ran out."),
             ("COOKED_SALMON", 4, "Онигири с лососем в дорогу.", "Rice balls with salmon for the road.")],
},
"dwarf": {
 "elder": [("GOLD_INGOT", 3, "Казна клана пустеет. Золото — кровь горы.", "The clan treasury is running low. Gold is the mountain's blood."),
           ("TORCH", 16, "Нижние штреки тонут во тьме. Факелы!", "The lower shafts are drowning in darkness. Torches!")],
 "lumberjack": [("OAK_LOG", 16, "Крепь для шахты: нужны дубовые брёвна.", "Pit props for the mine: I need oak logs."),
                ("CHARCOAL", 12, "Горн просит угля, а угольщик слёг.", "The forge wants charcoal, and the burner has fallen ill.")],
 "farmer": [("BROWN_MUSHROOM", 8, "Грибные грядки под горой. Нужна грибница.", "Mushroom beds under the mountain. I need spawn."),
            ("POTATO", 12, "Картофель растёт и в тени скал. Посадим!", "Potatoes grow even in the shadow of the rocks. Let us plant!")],
 "brewer": [("WHEAT", 16, "Стаут густой, как смола, и зерна на него вдвое.", "Stout as thick as tar needs twice the grain."),
            ("BROWN_MUSHROOM", 6, "Грибное пиво — гордость клана.", "Mushroom ale is the pride of the clan.")],
 "weaver": [("LEATHER", 6, "Фартуки кузнецов прожжены насквозь. Нужна кожа!", "The smiths' aprons are burnt through. Leather!"),
            ("STRING", 12, "Бороду старейшины к празднику заплетают шнурами.", "The elder's beard is braided with cords for the feast.")],
 "guard": [("IRON_INGOT", 4, "Секира стражника треснула. Железо на перековку.", "The guard's axe has cracked. Iron to reforge it."),
           ("SHIELD", 1, "Щит с руной клана. Мой раскололи.", "A shield with the clan rune. Mine was split.")],
 "builder": [("STONE_BRICKS", 24, "Своды чертога должны быть ровны.", "The vaults of the hall must be true."),
             ("COBBLED_DEEPSLATE", 16, "Глубинный камень на порог: крепче не бывает.", "Deepslate for the threshold: nothing is stronger.")],
 "courier": [("TORCH", 8, "Туннель до соседнего клана долгий и тёмный.", "The tunnel to the next clan is long and dark."),
             ("BREAD", 6, "Хлеб в дорогу под горой.", "Bread for the road under the mountain.")],
},
"elf": {
 "elder": [("LILY_OF_THE_VALLEY", 4, "Ландыши для обряда новой луны.", "Lilies of the valley for the new moon rite."),
           ("GLOW_BERRIES", 6, "Светоягоды озаряют рощу в праздник.", "Glow berries light the grove on feast nights.")],
 "lumberjack": [("BIRCH_SAPLING", 6, "Мы не рубим, не посадив. Нужны саженцы берёз.", "We never fell without planting. I need birch saplings."),
                ("DARK_OAK_SAPLING", 4, "Старый лес просит молодых дубов.", "The old wood asks for young oaks.")],
 "farmer": [("SWEET_BERRIES", 12, "Ягодные поляны редеют. Посадим заново.", "The berry glades are thinning. Let us replant them."),
            ("BONE_MEAL", 8, "Цветы в саду королевы поникли.", "The flowers in the queen's garden are drooping.")],
 "brewer": [("HONEY_BOTTLE", 3, "Нектар без мёда — роса, а не нектар.", "Nectar without honey is dew, not nectar."),
            ("GLOW_BERRIES", 6, "Светоягоды дают нектару сияние.", "Glow berries give the nectar its shine.")],
 "weaver": [("STRING", 16, "Паутинная нить на лёгкие плащи.", "Gossamer thread for light cloaks."),
            ("GREEN_DYE", 4, "Плащи разведчиков зелёные, как листва.", "Scout cloaks must be leaf green.")],
 "guard": [("ARROW", 20, "Лучники рощи берегут каждую стрелу, и всё равно их мало.", "The grove archers spare every arrow, and still there are too few."),
           ("FEATHER", 8, "Перья на оперение стрел.", "Feathers for fletching.")],
 "builder": [("BIRCH_PLANKS", 16, "Мосты меж деревьями — из светлой берёзы.", "The bridges between the trees are birch."),
             ("GLASS", 8, "Окна беседки должны ловить лунный свет.", "The bower windows must catch the moonlight.")],
 "courier": [("FEATHER", 4, "Перо в косе гонца — знак мира.", "A feather in the runner's braid is a sign of peace."),
             ("APPLE", 6, "Яблоки в дорогу меж рощами.", "Apples for the road between the groves.")],
},
}
lines = []
lines.append('package com.villagepax.sim.quest;')
lines.append('')
lines.append('import net.minecraft.item.Items;')
lines.append('')
lines.append('import java.util.List;')
lines.append('import java.util.Map;')
lines.append('')
lines.append('/**')
lines.append(' * Поручения по народам: у каждого ремесла каждого народа свои просьбы')
lines.append(' * и свои слова к ним.')
lines.append(' * <p>')
lines.append(' * Общие поручения ({@link Errands}) просят одно и то же у всех народов')
lines.append(' * и одними словами, и игрок их не отличал от квеста к квесту: «руки')
lines.append(' * заняты топором» говорил и норманн, и эльф. Здесь просьба — маленькая')
lines.append(' * история: норманнский пивовар варит сидр, майя — балче на мёде диких')
lines.append(' * пчёл, гном — грибное пиво. Ключ слов собирается из народа, ремесла')
lines.append(' * и номера просьбы: {@code villagepax.errand.<народ>.<ремесло>.<номер>}.')
lines.append(' * <p>')
lines.append(' * Таблица порождена из одного списка вместе со словарём, поэтому')
lines.append(' * правится там же, где и слова ({@code tools/make-errands.py}), — иначе\n * разошлась бы с ними.')
lines.append(' */')
lines.append('final class ErrandBook {')
lines.append('')
lines.append('    static final Map<String, Map<String, List<Errands.Ask>>> BY_PEOPLE = Map.of(')
peoples = []
for culture, trades in E.items():
    tl = []
    for trade, asks in trades.items():
        al = ', '.join(f'new Errands.Ask(Items.{item}, {count})' for item, count, ru, en in asks)
        tl.append(f'                    "{trade}", List.of({al})')
    peoples.append(f'            "{culture}", Map.of(\n' + ',\n'.join(tl) + ')')
lines.append(',\n'.join(peoples) + ');')
lines.append('')
lines.append('    private ErrandBook() {')
lines.append('    }')
lines.append('}')
open('src/main/java/com/villagepax/sim/quest/ErrandBook.java', 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
ru = collections.OrderedDict(); en = collections.OrderedDict()
for culture, trades in E.items():
    for trade, asks in trades.items():
        for i, (item, count, r, e) in enumerate(asks):
            k = f'villagepax.errand.{culture}.{trade}.{i}'
            ru[k] = r; en[k] = e
for lang, words in (('ru_ru', ru), ('en_us', en)):
    path = f'src/main/resources/assets/villagepax/lang/{lang}.json'
    old = json.load(open(path, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
    out = collections.OrderedDict()
    for key, value in old.items():
        if key in words:
            continue
        out[key] = value
        if key == 'villagepax.errand.courier':
            out.update(words)
    open(path, 'w', encoding='utf-8').write(json.dumps(out, indent=2, ensure_ascii=False) + '\n')
print('поручений:', len(ru))
