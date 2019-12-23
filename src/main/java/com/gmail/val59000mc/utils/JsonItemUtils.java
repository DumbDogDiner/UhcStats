package com.gmail.val59000mc.utils;

import com.gmail.val59000mc.exceptions.ParseException;
import com.google.gson.*;
import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonItemUtils{

    public static String getItemJson(ItemStack item){
        JsonObject json = new JsonObject();
        json.addProperty("type", item.getType().toString());
        if (item.getAmount() != 1){
            json.addProperty("amount", item.getAmount());
        }
        if (item.getDurability() != 0){
            json.addProperty("durability", item.getDurability());
        }
        if (item.hasItemMeta()){
            ItemMeta meta = item.getItemMeta();

            if (meta.hasDisplayName()){
                json.addProperty("display-name", meta.getDisplayName().replace('\u00a7', '&'));
            }
            if (meta.hasLore()){
                JsonArray lore = new JsonArray();
                meta.getLore().forEach(line -> lore.add(new JsonPrimitive(line.replace('\u00a7', '&'))));
                json.add("lore", lore);
            }
            if (meta.hasEnchants()){
                Map<Enchantment, Integer> enchantments = meta.getEnchants();
                JsonArray jsonEnchants = new JsonArray();
                for (Enchantment enchantment : enchantments.keySet()){
                    JsonObject jsonEnchant = new JsonObject();
                    jsonEnchant.addProperty("type", enchantment.getName());
                    jsonEnchant.addProperty("level", enchantments.get(enchantment));
                    jsonEnchants.add(jsonEnchant);
                }
                json.add("enchantments", jsonEnchants);
            }

            if (meta instanceof PotionMeta){
                PotionMeta potionMeta = (PotionMeta) meta;

                if (!potionMeta.getCustomEffects().isEmpty()) {
                    JsonArray customEffects = new JsonArray();

                    for (PotionEffect effect : potionMeta.getCustomEffects()) {
                        JsonObject jsonEffect = new JsonObject();
                        jsonEffect.addProperty("type", effect.getType().getName());
                        jsonEffect.addProperty("duration", effect.getDuration());
                        jsonEffect.addProperty("amplifier", effect.getAmplifier());
                        customEffects.add(jsonEffect);
                    }
                    json.add("custom-effects", customEffects);
                }
            }
            else if (meta instanceof EnchantmentStorageMeta){
                EnchantmentStorageMeta enchantmentMeta = (EnchantmentStorageMeta) meta;
                Map<Enchantment, Integer> enchantments = enchantmentMeta.getStoredEnchants();
                JsonArray jsonEnchants = new JsonArray();
                for (Enchantment enchantment : enchantments.keySet()){
                    JsonObject jsonEnchant = new JsonObject();
                    jsonEnchant.addProperty("type", enchantment.getName());
                    jsonEnchant.addProperty("level", enchantments.get(enchantment));
                    jsonEnchants.add(jsonEnchant);
                }
                json.add("enchantments", jsonEnchants);
            }
        }
        return json.toString();
    }

    public static ItemStack getItemFromJson(String jsonString) throws ParseException {
        try {
            JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
            Material material;

            try {
                material = Material.valueOf(json.get("type").getAsString());
            }catch (IllegalArgumentException ex){
                throw new ParseException("Invalid item type: " + json.get("type").getAsString());
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()){
                switch (entry.getKey()){
                    case "type":
                        continue;
                    case "amount":
                        item.setAmount(entry.getValue().getAsInt());
                        break;
                    case "durability":
                        item.setDurability(entry.getValue().getAsShort());
                        break;
                    case "display-name":
                        meta.setDisplayName(entry.getValue().getAsString());
                        break;
                    case "lore":
                        meta = parseLore(meta, entry.getValue().getAsJsonArray());
                        break;
                    case "enchantments":
                        meta = parseEnchantments(meta, entry.getValue().getAsJsonArray());
                        break;
                    case "custom-effects":
                        meta = parseCustomPotionEffects(meta, entry.getValue().getAsJsonArray());
                        break;
                }
            }

            item.setItemMeta(meta);
            return item;
        }catch (Exception ex){
            ex.printStackTrace();
            if (ex instanceof ParseException){
                throw ex;
            }

            ParseException exception = new ParseException(ex.getMessage());
            ex.setStackTrace(ex.getStackTrace());
            throw exception;
        }
    }

    private static ItemMeta parseLore(ItemMeta meta, JsonArray jsonArray){
        Iterator<JsonElement> lines = jsonArray.iterator();
        List<String> lore = new ArrayList<>();
        while (lines.hasNext()){
            lore.add(lines.next().getAsString());
        }
        meta.setLore(lore);
        return meta;
    }

    private static ItemMeta parseEnchantments(ItemMeta meta, JsonArray jsonArray){
        EnchantmentStorageMeta enchantmentMeta = null;

        if (meta instanceof EnchantmentStorageMeta){
            enchantmentMeta = (EnchantmentStorageMeta) meta;
        }

        Iterator<JsonElement> enchants = jsonArray.iterator();
        while (enchants.hasNext()){
            JsonObject enchant = enchants.next().getAsJsonObject();

            Enchantment enchantment = Enchantment.getByName(enchant.get("type").getAsString());
            Validate.notNull(enchantment, "Unknown enchantment type: " + enchant.get("type").getAsString());

            int level = enchant.get("level").getAsInt();
            if (enchantmentMeta == null) {
                meta.addEnchant(enchantment, level, true);
            }else{
                enchantmentMeta.addStoredEnchant(enchantment, level, true);
            }
        }
        return enchantmentMeta == null ? meta : enchantmentMeta;
    }

    private static ItemMeta parseCustomPotionEffects(ItemMeta meta, JsonArray jsonArray) throws ParseException {
        PotionMeta potionMeta = (PotionMeta) meta;
        Iterator<JsonElement> effects = jsonArray.iterator();

        while (effects.hasNext()){
            JsonObject effect = effects.next().getAsJsonObject();
            PotionEffectType type = PotionEffectType.getByName(effect.get("type").getAsString());

            if (type == null){
                throw new ParseException("Invalid potion type: " + effect.get("type").getAsString());
            }

            int duration;
            int amplifier;

            try {
                duration = effect.get("duration").getAsInt();
                amplifier = effect.get("amplifier").getAsInt();
            }catch (NullPointerException ex){
                throw new ParseException("Missing duration or amplifier tag for: " + effect.toString());
            }

            PotionEffect potionEffect = new PotionEffect(type, duration, amplifier);
            potionMeta.addCustomEffect(potionEffect, true);
        }

        return potionMeta;
    }

}