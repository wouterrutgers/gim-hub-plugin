/*
 * Copyright (c) 2022, Thource <https://github.com/Thource>
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
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
 *
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

package gimhub.items.containers;

import java.util.List;
import java.util.Map;
import lombok.Value;

final class StashDefinitions {
    private StashDefinitions() {}

    @Value
    static class Definition {
        List<Integer> items;
        List<String> alternatives;
    }

    static final Map<Integer, Definition> UNITS = Map.ofEntries(
            Map.entry(28958, new Definition(List.of(1205, 1, 1153, 1, 1635, 1), List.of())),
            Map.entry(28959, new Definition(List.of(1137, 1, 1639, 1, 1005, 1), List.of())),
            Map.entry(28960, new Definition(List.of(1097, 1, 1191, 1, 1295, 1), List.of())),
            Map.entry(28961, new Definition(List.of(1075, 1, 1269, 1, 1141, 1), List.of())),
            Map.entry(28962, new Definition(List.of(1067, 1, 845, 1, 1696, 1), List.of())),
            Map.entry(28963, new Definition(List.of(1019, 1, 1095, 1, 1424, 1), List.of())),
            Map.entry(28964, new Definition(List.of(1169, 1, 1083, 1, 1656, 1), List.of())),
            Map.entry(28965, new Definition(List.of(1833, 1, 1059, 1, 1061, 1), List.of())),
            Map.entry(28966, new Definition(List.of(1115, 1, 1097, 1, 1155, 1), List.of())),
            Map.entry(28967, new Definition(List.of(640, 1, 654, 1, 843, 1), List.of())),
            Map.entry(28968, new Definition(List.of(1101, 1, 1637, 1, 839, 1), List.of())),
            Map.entry(28969, new Definition(List.of(1654, 1, 1635, 1, 1237, 1), List.of())),
            Map.entry(28970, new Definition(List.of(638, 1, 4300, 1, 1335, 1), List.of())),
            Map.entry(28971, new Definition(List.of(640, 1, 4300, 1, 5525, 1), List.of())),
            Map.entry(28972, new Definition(List.of(1157, 1, 1119, 1, 1081, 1), List.of())),
            Map.entry(28973, new Definition(List.of(579, 1, 1307, 1, 4310, 1), List.of())),
            Map.entry(28974, new Definition(List.of(1833, 1, 648, 1, 1353, 1), List.of())),
            Map.entry(28975, new Definition(List.of(1133, 1, 1075, 1, 1379, 1), List.of())),
            Map.entry(28976, new Definition(List.of(658, 1, 642, 1, 1095, 1), List.of())),
            Map.entry(28977, new Definition(List.of(1639, 1, 1694, 1, 1103, 1), List.of())),
            Map.entry(28978, new Definition(List.of(1169, 1, 1115, 1, 1059, 1), List.of())),
            Map.entry(28979, new Definition(List.of(1167, 1, 577, 1, 1323, 1), List.of())),
            Map.entry(28980, new Definition(List.of(1005, 1, 628, 1, 1059, 1), List.of())),
            Map.entry(28981, new Definition(List.of(1131, 1, 1095, 1, 1351, 1), List.of())),
            Map.entry(28982, new Definition(List.of(1101, 1, 1095, 1, 1169, 1), List.of())),
            Map.entry(28983, new Definition(List.of(1361, 1, 1169, 1, 1641, 1), List.of())),
            Map.entry(28984, new Definition(List.of(1273, 1, 1125, 1, 1191, 1), List.of())),
            Map.entry(28985, new Definition(List.of(1013, 1, 636, 1, 5533, 1), List.of())),
            Map.entry(28986, new Definition(List.of(5527, 1, 1383, 1), List.of())),
            Map.entry(28987, new Definition(List.of(638, 1, 1071, 1, 1309, 1), List.of())),
            Map.entry(28988, new Definition(List.of(1085, 1, 851, 1), List.of())),
            Map.entry(28989, new Definition(List.of(2942, 1, 1193, 1, 1159, 1), List.of())),
            Map.entry(28990, new Definition(List.of(1099, 1, 1143, 1), List.of("Ring of dueling"))),
            Map.entry(28991, new Definition(List.of(1698, 1, 1329, 1), List.of("Any team cape"))),
            Map.entry(28992, new Definition(List.of(1119, 1, 853, 1), List.of("Any team cape"))),
            Map.entry(28993, new Definition(List.of(1193, 1, 2568, 1, 1099, 1), List.of())),
            Map.entry(28994, new Definition(List.of(1757, 1, 1145, 1, 6324, 1), List.of())),
            Map.entry(28995, new Definition(List.of(1109, 1, 1099, 1, 1698, 1), List.of())),
            Map.entry(28996, new Definition(List.of(1135, 1, 1099, 1, 1177, 1), List.of())),
            Map.entry(28997, new Definition(List.of(658, 1, 6328, 1, 1267, 1), List.of())),
            Map.entry(28998, new Definition(List.of(630, 1, 1131, 1, 2961, 1), List.of())),
            Map.entry(28999, new Definition(List.of(1381, 1, 1173, 1), List.of("Bruise blue snelm (pointed)"))),
            Map.entry(29000, new Definition(List.of(1381, 1, 1155, 1, 1731, 1), List.of())),
            Map.entry(29001, new Definition(List.of(851, 1, 1099, 1, 1137, 1), List.of())),
            Map.entry(29002, new Definition(List.of(3200, 1, 4093, 1, 1643, 1), List.of())),
            Map.entry(29003, new Definition(List.of(1183, 1, 8872, 1, 1121, 1), List.of())),
            Map.entry(29004, new Definition(List.of(1295, 1, 2499, 1, 4095, 1), List.of())),
            Map.entry(29005, new Definition(List.of(1757, 1, 1061, 1, 1059, 1), List.of())),
            Map.entry(29006, new Definition(List.of(1073, 1, 1123, 1, 1161, 1), List.of())),
            Map.entry(29007, new Definition(List.of(2487, 1, 4129, 1, 1211, 1), List.of())),
            Map.entry(29008, new Definition(List.of(1287, 1, 1694, 1, 1091, 1), List.of())),
            Map.entry(29009, new Definition(List.of(1079, 1, 1115, 1, 2487, 1), List.of())),
            Map.entry(29010, new Definition(List.of(2890, 1, 2493, 1, 1347, 1), List.of())),
            Map.entry(29011, new Definition(List.of(2499, 1, 2487, 1), List.of())),
            Map.entry(29012, new Definition(List.of(2570, 1, 1704, 1, 1317, 1), List.of())),
            Map.entry(29013, new Definition(List.of(1183, 1, 2487, 1, 1275, 1), List.of())),
            Map.entry(29014, new Definition(List.of(1643, 1, 1731, 1), List.of())),
            Map.entry(29015, new Definition(List.of(1163, 1, 2493, 1, 1393, 1), List.of())),
            Map.entry(29016, new Definition(List.of(1071, 1, 2570, 1, 1359, 1), List.of())),
            Map.entry(29017, new Definition(List.of(4089, 1, 5016, 1, 1127, 1), List.of())),
            Map.entry(29018, new Definition(List.of(1401, 1, 11092, 1, 4131, 1), List.of())),
            Map.entry(29019, new Definition(List.of(), List.of("Any stole", "Any heraldic rune shield"))),
            Map.entry(29020, new Definition(List.of(), List.of("Any headband", "Any crozier"))),
            Map.entry(29021, new Definition(List.of(1247, 1, 1079, 1), List.of("Any rune heraldic helm"))),
            Map.entry(29022, new Definition(List.of(4091, 1), List.of("Any rune heraldic shield"))),
            Map.entry(29023, new Definition(List.of(3202, 1, 1127, 1, 1725, 1), List.of())),
            Map.entry(29024, new Definition(List.of(2495, 1), List.of("Any dragon spear"))),
            Map.entry(29025, new Definition(List.of(4131, 1, 9674, 1, 1645, 1), List.of())),
            Map.entry(29026, new Definition(List.of(2497, 1, 7445, 1), List.of("Spotted cape"))),
            Map.entry(
                    29027,
                    new Definition(
                            List.of(),
                            List.of(
                                    "Any mitre",
                                    "Rune crossbow",
                                    "Climbing boots",
                                    "Ring of visibility or ring of shadows"))),
            Map.entry(29028, new Definition(List.of(10148, 1), List.of())),
            Map.entry(29029, new Definition(List.of(), List.of("Any god book"))),
            Map.entry(29030, new Definition(List.of(7462, 1), List.of("Any amulet of glory", "Any dragon med helm"))),
            Map.entry(29031, new Definition(List.of(6724, 1), List.of("Combat bracelet", "Helm of neitiznot"))),
            Map.entry(29032, new Definition(List.of(2491, 1, 9731, 1), List.of("Lava battlestaff"))),
            Map.entry(29033, new Definition(List.of(3389, 1, 1303, 1), List.of("Dragon boots"))),
            Map.entry(29034, new Definition(List.of(3122, 1, 3387, 1), List.of("Any rune heraldic helm"))),
            Map.entry(29035, new Definition(List.of(2487, 1, 1093, 1, 4084, 1), List.of("Any dragon spear"))),
            Map.entry(29036, new Definition(List.of(4093, 1, 1201, 1), List.of("Any Bob shirt"))),
            Map.entry(29037, new Definition(List.of(1052, 1), List.of("Any dragon battleaxe", "Any amulet of glory"))),
            Map.entry(29038, new Definition(List.of(1702, 1, 2568, 1), List.of("Castle wars bracelet"))),
            Map.entry(29039, new Definition(List.of(1664, 1, 859, 1), List.of("Any pirate bandana"))),
            Map.entry(
                    29040,
                    new Definition(
                            List.of(6524, 1, 11037, 1, 1127, 1),
                            List.of("Any dragon med helm", "Uncharged Amulet of glory"))),
            Map.entry(29041, new Definition(List.of(6568, 1), List.of("Any dragon 2h sword", "Bandos boots"))),
            Map.entry(29042, new Definition(List.of(), List.of("Any full barrows set"))),
            Map.entry(29043, new Definition(List.of(4101, 1, 4103, 1), List.of("Any iban's staff"))),
            Map.entry(29044, new Definition(List.of(3387, 1), List.of("Dragon sq shield", "Any boater"))),
            Map.entry(29045, new Definition(List.of(), List.of("Pharaoh's sceptre", "Full set of menaphite robes"))),
            Map.entry(29046, new Definition(List.of(10828, 1, 4131, 1), List.of("Dragon or Crystal pickaxe"))),
            Map.entry(
                    29047,
                    new Definition(
                            List.of(),
                            List.of(
                                    "Any dragon battleaxe",
                                    "Dragon defender or Avernic defender",
                                    "Any slayer helmet"))),
            Map.entry(29048, new Definition(List.of(2491, 1, 2497, 1, 2503, 1), List.of())),
            Map.entry(29049, new Definition(List.of(6522, 1), List.of("Fire cape"))),
            Map.entry(29050, new Definition(List.of(), List.of("Crystal Bow"))),
            Map.entry(29051, new Definition(List.of(12480, 1, 12273, 1), List.of("Bandos godsword"))),
            Map.entry(29052, new Definition(List.of(), List.of("Arclight or Emberlight", "Amulet of the damned"))),
            Map.entry(29053, new Definition(List.of(2503, 1, 2491, 1, 12524, 1), List.of())),
            Map.entry(29054, new Definition(List.of(2657, 1, 10858, 1), List.of())),
            Map.entry(29055, new Definition(List.of(), List.of("Zamorak godsword"))),
            Map.entry(29056, new Definition(List.of(10394, 1, 1523, 1), List.of())),
            Map.entry(
                    29057,
                    new Definition(
                            List.of(716, 1), List.of("Dragon plateskirt", "Climbing boots", "Dragon chainbody"))),
            Map.entry(29058, new Definition(List.of(5547, 1, 1052, 1), List.of("Any ring of wealth"))),
            Map.entry(29059, new Definition(List.of(1052, 1, 6135, 1), List.of("Abyssal whip"))),
            Map.entry(29060, new Definition(List.of(), List.of("Dragon or Crystal axe"))),
            Map.entry(34647, new Definition(List.of(1345, 1, 2570, 1, 4127, 1), List.of())),
            Map.entry(34736, new Definition(List.of(1635, 1, 1654, 1), List.of())),
            Map.entry(34737, new Definition(List.of(1949, 1, 1007, 1), List.of())),
            Map.entry(34738, new Definition(List.of(1351, 1, 1061, 1), List.of())),
            Map.entry(34953, new Definition(List.of(5541, 1), List.of("Bryophyta's staff"))),
            Map.entry(41758, new Definition(List.of(13381, 1, 20706, 1), List.of("Farmer's strawhat"))),
            Map.entry(50738, new Definition(List.of(1658, 1, 1011, 1, 644, 1), List.of())),
            Map.entry(50739, new Definition(List.of(), List.of("Any piece of Sunfire Fanatic armour"))),
            Map.entry(
                    50740,
                    new Definition(
                            List.of(28988, 1), List.of("Blue moon helm", "Blue moon chestplate", "Blue moon tassets"))),
            Map.entry(54275, new Definition(List.of(30073, 1, 30082, 1), List.of())),
            Map.entry(55403, new Definition(List.of(851, 1, 1698, 1, 1069, 1), List.of())),
            Map.entry(55404, new Definition(List.of(579, 1, 577, 1), List.of())),
            Map.entry(55405, new Definition(List.of(1303, 1, 1127, 1, 1093, 1), List.of())),
            Map.entry(58130, new Definition(List.of(1025, 1, 1321, 1), List.of())),
            Map.entry(58131, new Definition(List.of(7539, 1, 7537, 1), List.of())),
            Map.entry(58132, new Definition(List.of(32386, 1, 31583, 1), List.of())));
}
