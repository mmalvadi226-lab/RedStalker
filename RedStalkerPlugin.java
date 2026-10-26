package com.kdmv.redstalker;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class RedStalkerPlugin extends JavaPlugin implements Listener {
    private NamespacedKey marker;
    private UUID stalkerId;
    private long nextReturnAt = 0L;
    private boolean escapeScheduled = false;

    @Override
    public void onEnable() {
        marker = new NamespacedKey(this, "red_stalker");
        Bukkit.getPluginManager().registerEvents(this, this);
        startHauntingLoop();
        getLogger().info("RedStalker enabled. Use /redstalker spawn or /redstalker stop.");
    }

    @Override
    public void onDisable() {
        removeStalker();
    }

    private boolean isStalker(Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
            .has(marker, PersistentDataType.BYTE);
    }

    private ItemStack redLeather(Material type, String displayName) {
        ItemStack item = new ItemStack(type);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.fromRGB(210, 15, 35));
            meta.setDisplayName("§c§l" + displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack namedRod() {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lHaunting Hook");
            rod.setItemMeta(meta);
        }
        return rod;
    }

    private void equip(Zombie mob) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;
        eq.setHelmet(redLeather(Material.LEATHER_HELMET, "Red Stalker Hood"));
        eq.setChestplate(redLeather(Material.LEATHER_CHESTPLATE, "Red Stalker Armor"));
        eq.setLeggings(redLeather(Material.LEATHER_LEGGINGS, "Red Stalker Leggings"));
        eq.setBoots(redLeather(Material.LEATHER_BOOTS, "Red Stalker Boots"));
        eq.setItemInMainHand(namedRod());
        eq.setHelmetDropChance(0);
        eq.setChestplateDropChance(0);
        eq.setLeggingsDropChance(0);
        eq.setBootsDropChance(0);
        eq.setItemInMainHandDropChance(0);
    }

    private Player nearestPlayer(Location from, double maxDistance) {
        Player best = null;
        double bestD = maxDistance * maxDistance;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline() || p.isDead() || p.getWorld() != from.getWorld()) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private void spawnStalker(Player near) {
        if (near == null || !near.isOnline()) return;
        removeStalker();

        Location at = near.getLocation().clone().add(
            near.getLocation().getDirection().multiply(-7));
        at.setY(near.getLocation().getY());
        World world = near.getWorld();

        Zombie mob = (Zombie) world.spawnEntity(at, EntityType.ZOMBIE);
        mob.setAdult();
        mob.setCustomName("§c§lThe Red Stalker");
        mob.setCustomNameVisible(false);
        mob.setSilent(true);
        mob.setPersistent(true);
        mob.setCanPickupItems(false);
        mob.setRemoveWhenFarAway(false);
        mob.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
            Integer.MAX_VALUE, 0, false, false, false));
        equip(mob);

        stalkerId = mob.getUniqueId();
        escapeScheduled = false;
        world.playSound(near.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1f, 0.65f);
        near.sendMessage("§4§lSomething is watching you...");
    }

    private void removeStalker() {
        if (stalkerId != null) {
            Entity e = Bukkit.getEntity(stalkerId);
            if (e != null) e.remove();
        }
        stalkerId = null;
        escapeScheduled = false;
    }

    private void vanishAndReturn(Zombie mob, Player attacker) {
        if (escapeScheduled) return;
        escapeScheduled = true;
        Location loc = mob.getLocation();
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 25, 0.4, 0.6, 0.4, 0.03);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        mob.remove();
        stalkerId = null;
        nextReturnAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        Bukkit.broadcastMessage("§c§lThe Red Stalker vanished into the darkness...");
        if (attacker != null) attacker.sendMessage("§cIt will return...");
        escapeScheduled = false;
    }

    private void startHauntingLoop() {
        new BukkitRunnable() {
            @Override public void run() {
                if (stalkerId == null) {
                    if (System.currentTimeMillis() < nextReturnAt || Bukkit.getOnlinePlayers().isEmpty()) return;
                    if (Math.random() < 0.025) {
                        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
                        spawnStalker(players[(int) (Math.random() * players.length)]);
                    }
                    return;
                }

                Entity e = Bukkit.getEntity(stalkerId);
                if (!(e instanceof Zombie mob) || mob.isDead()) {
                    stalkerId = null;
                    return;
                }

                Player target = nearestPlayer(mob.getLocation(), 32);
                if (target == null) return;

                Location from = mob.getLocation();
                Location to = target.getLocation();
                Vector delta = to.toVector().subtract(from.toVector());
                if (delta.lengthSquared() > 0.01) {
                    mob.setRotation((float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ())),
                        (float) -Math.toDegrees(Math.atan2(delta.getY(),
                            Math.sqrt(delta.getX()*delta.getX() + delta.getZ()*delta.getZ()))));
                }
                mob.setTarget(target);

                // Surprise attack from behind when close; normal mob AI handles movement and damage.
                if (from.distanceSquared(to) < 5.0 && Math.random() < 0.10) {
                    target.playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.8f, 0.6f);
                }
            }
        }.runTaskTimer(this, 40L, 20L);
    }

    @EventHandler
    public void onStalkerHit(EntityDamageByEntityEvent event) {
        if (!isStalker(event.getEntity())) return;
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof Zombie mob) {
            event.setCancelled(true);
            vanishAndReturn(mob, player);
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("redstalker")) return false;
        if (!sender.hasPermission("redstalker.admin")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§cUse: /redstalker <spawn|stop>");
            return true;
        }
        if (args[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Run this command in-game.");
                return true;
            }
            spawnStalker(player);
            sender.sendMessage("§aThe Red Stalker has spawned.");
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            removeStalker();
            sender.sendMessage("§aThe Red Stalker was removed.");
            return true;
        }
        sender.sendMessage("§cUse: /redstalker <spawn|stop>");
        return true;
    }
}
