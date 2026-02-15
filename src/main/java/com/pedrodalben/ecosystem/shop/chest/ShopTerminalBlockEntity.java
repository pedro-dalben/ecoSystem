package com.pedrodalben.ecosystem.shop.chest;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Block entity for the Shop Terminal.
 * Stores: owner UUID, currency, item listings (item ID, buy price, sell price),
 * linked chest position.
 */
public class ShopTerminalBlockEntity extends BlockEntity {

    private UUID ownerUuid;
    private String ownerName = "";
    private String currencyId = "money";
    private final List<ShopListing> listings = new ArrayList<>();
    @Nullable
    private BlockPos linkedChestPos;

    public record ShopListing(String itemId, String displayName, long buyPrice, long sellPrice,
            boolean canBuy, boolean canSell) {
    }

    public ShopTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(EcoSystemMod.SHOP_TERMINAL_BE.get(), pos, state);
    }

    // ==================== NBT (PERSISTENCE) ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (ownerUuid != null)
            tag.putUUID("owner", ownerUuid);
        tag.putString("ownerName", ownerName);
        tag.putString("currency", currencyId);

        if (linkedChestPos != null) {
            tag.putInt("chestX", linkedChestPos.getX());
            tag.putInt("chestY", linkedChestPos.getY());
            tag.putInt("chestZ", linkedChestPos.getZ());
        }

        ListTag listingsTag = new ListTag();
        for (ShopListing listing : listings) {
            CompoundTag lt = new CompoundTag();
            lt.putString("itemId", listing.itemId());
            lt.putString("displayName", listing.displayName());
            lt.putLong("buyPrice", listing.buyPrice());
            lt.putLong("sellPrice", listing.sellPrice());
            lt.putBoolean("canBuy", listing.canBuy());
            lt.putBoolean("canSell", listing.canSell());
            listingsTag.add(lt);
        }
        tag.put("listings", listingsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.hasUUID("owner"))
            ownerUuid = tag.getUUID("owner");
        ownerName = tag.getString("ownerName");
        currencyId = tag.getString("currency");

        if (tag.contains("chestX")) {
            linkedChestPos = new BlockPos(tag.getInt("chestX"), tag.getInt("chestY"), tag.getInt("chestZ"));
        }

        listings.clear();
        if (tag.contains("listings", Tag.TAG_LIST)) {
            ListTag listingsTag = tag.getList("listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < listingsTag.size(); i++) {
                CompoundTag lt = listingsTag.getCompound(i);
                listings.add(new ShopListing(
                        lt.getString("itemId"),
                        lt.getString("displayName"),
                        lt.getLong("buyPrice"),
                        lt.getLong("sellPrice"),
                        lt.getBoolean("canBuy"),
                        lt.getBoolean("canSell")));
            }
        }
    }

    // ==================== CHEST LINKING ====================

    /**
     * Auto-detect an adjacent chest. Searches 6 faces.
     */
    @Nullable
    public BlockPos findAdjacentChest() {
        if (level == null)
            return null;
        for (Direction dir : Direction.values()) {
            BlockPos adj = worldPosition.relative(dir);
            BlockEntity be = level.getBlockEntity(adj);
            if (be instanceof ChestBlockEntity) {
                return adj;
            }
        }
        return null;
    }

    /**
     * Get the linked chest as a Container (for stock management).
     */
    @Nullable
    public Container getLinkedChest() {
        if (level == null || linkedChestPos == null)
            return null;
        BlockEntity be = level.getBlockEntity(linkedChestPos);
        if (be instanceof Container container) {
            return container;
        }
        return null;
    }

    /**
     * Count items of a given type in the linked chest.
     */
    public int countStock(String itemId) {
        Container chest = getLinkedChest();
        if (chest == null)
            return 0;

        int count = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty()
                    && stack.getItem().builtInRegistryHolder().key().location().toString().equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // ==================== ACCESSORS ====================

    public boolean isOwner(UUID uuid) {
        return ownerUuid != null && ownerUuid.equals(uuid);
    }

    public boolean isConfigured() {
        return !listings.isEmpty() && linkedChestPos != null;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        setChanged();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String name) {
        this.ownerName = name;
        setChanged();
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(String id) {
        this.currencyId = id;
        setChanged();
    }

    @Nullable
    public BlockPos getLinkedChestPos() {
        return linkedChestPos;
    }

    public void setLinkedChestPos(@Nullable BlockPos pos) {
        this.linkedChestPos = pos;
        setChanged();
    }

    public List<ShopListing> getListings() {
        return listings;
    }

    public void addListing(ShopListing listing) {
        listings.add(listing);
        setChanged();
    }

    public void clearListings() {
        listings.clear();
        setChanged();
    }
}
