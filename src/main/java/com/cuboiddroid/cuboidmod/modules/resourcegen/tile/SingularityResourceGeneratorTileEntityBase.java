package com.cuboiddroid.cuboidmod.modules.resourcegen.tile;

import com.cuboiddroid.cuboidmod.modules.collapser.item.QuantumSingularityItem;
import com.cuboiddroid.cuboidmod.modules.resourcegen.recipe.ResourceGeneratingRecipe;
import com.cuboiddroid.cuboidmod.setup.ModItems;
import com.cuboiddroid.cuboidmod.setup.ModTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntityTicker ;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("rawtypes")
public abstract class SingularityResourceGeneratorTileEntityBase extends BlockEntity implements BlockEntityTicker  {

    private ItemStackHandler inputItemHandler = createInputHandler();
    private ItemStackHandler outputItemHandler = createOutputHandler();
    private CombinedInvWrapper combinedItemHandler = new CombinedInvWrapper(inputItemHandler, outputItemHandler);

    // Never create lazy optionals in getCapability. Always place them as fields in the tile entity:
    private LazyOptional<IItemHandler> inputHandler = LazyOptional.of(() -> inputItemHandler);
    private LazyOptional<IItemHandler> outputHandler = LazyOptional.of(() -> outputItemHandler);
    private LazyOptional<IItemHandler> combinedHandler = LazyOptional.of(() -> combinedItemHandler);

    public static final int INPUT_SLOTS = 1;
    public static final int OUTPUT_SLOTS = 1;
    public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;

    public static final int SINGULARITY_INPUT = 0;
    public static final int OUTPUT = 1;

    private int itemsAvailable;
    private int processingTime;
    private int ticksPerOperation;
    private int itemsPerOperation;
    private int maxItems;

    private ResourceGeneratingRecipe cachedRecipe = null;

    public SingularityResourceGeneratorTileEntityBase(BlockEntityType<?> tileEntityType, int ticksPerOperation, int itemsPerOperation, int maxItems) {
        this(tileEntityType, null, null, ticksPerOperation, itemsPerOperation, maxItems);
    }

    public SingularityResourceGeneratorTileEntityBase(BlockEntityType<?> tileEntityType, BlockPos pos, BlockState state, int ticksPerOperation, int itemsPerOperation, int maxItems) {
        super(tileEntityType, pos, state);

        this.itemsAvailable = 0;
        this.processingTime = 0;
        this.ticksPerOperation = ticksPerOperation;
        this.itemsPerOperation = itemsPerOperation;
        this.maxItems = maxItems;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        inputHandler.invalidate();
        outputHandler.invalidate();
        combinedHandler.invalidate();
    }

    public static <T extends SingularityResourceGeneratorTileEntityBase> void gameTick(Level level, BlockPos worldPosition, BlockState blockState, T entity) {
        entity.tick(level, worldPosition, blockState, entity);
    }

    @Override
    public void tick(Level level, BlockPos worldPosition, BlockState blockState, BlockEntity entity) {
        if (level.isClientSide)
            return;

        ResourceGeneratingRecipe recipe = getRecipe();

        if (recipe == null) {
            this.itemsAvailable = 0;
            this.processingTime = 0;
            setChanged();
        } else {
            if (processingTime > 0) {
                processingTime--;
                if (processingTime <= 0) {
                    if (this.itemsPerOperation > 0) {
                        this.itemsAvailable += (int) Math.ceil(this.itemsPerOperation * recipe.getOutputMultiplier());

                        if (this.itemsAvailable > maxItems)
                            this.itemsAvailable = maxItems;
                    }
                }
                setChanged();
            }

            if (processingTime <= 0) {
                ItemStack generatedItem = getWorkOutput(recipe);

                ItemStack outputStack = combinedItemHandler.getStackInSlot(OUTPUT);
                if (outputStack.isEmpty()) {
                    // empty output stack - so we can just shift over the number available or
                    // the max stack size, whichever is smaller
                    generatedItem.setCount(Math.min(this.itemsAvailable, generatedItem.getMaxStackSize()));
                    this.itemsAvailable -= generatedItem.getCount();
                    this.combinedItemHandler.setStackInSlot(OUTPUT, generatedItem);
                } else if (!outputStack.is(generatedItem.getItem())) {
                    // the output stack contains a different item - do nothing!
                    // the available items will increase, but the output slot will need
                    // to be emptied before getting our hands on some of it
                } else {
                    // output stack has stuff in it - how much?
                    int count = outputStack.getCount();

                    // get a copy to work on - we should not change the stack returned from
                    // getStackInSlot(...)
                    ItemStack newOutputStack = outputStack.copy();
                    if (count + this.itemsAvailable < generatedItem.getMaxStackSize()) {
                        // what is in the output stack, plus the number of items available,
                        // is less than the max stack size - so we can move them all across
                        newOutputStack.grow(this.itemsAvailable);
                        this.itemsAvailable = 0;
                    } else {
                        // we've got enough to top up the output stack to max, and have
                        // some left - so work out how many we will move
                        int itemsToMove = generatedItem.getMaxStackSize() - count;
                        newOutputStack.grow(itemsToMove);
                        this.itemsAvailable -= itemsToMove;
                    }

                    combinedItemHandler.setStackInSlot(OUTPUT, newOutputStack);
                }

                // start work on the next one
                processingTime = (int) Math.ceil(ticksPerOperation * recipe.getWorkTimeMultiplier());
            }
        }

        if (blockState.getValue(BlockStateProperties.LIT) != processingTime > 0) {
            level.setBlock(worldPosition, blockState.setValue(BlockStateProperties.LIT, processingTime > 0), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public ResourceGeneratingRecipe getRecipe() {
        if (this.level == null
                || this.inputItemHandler.getStackInSlot(0).isEmpty())
            return null;

        // make an inventory
        Container inv = getInputsAsInventory();

        if (cachedRecipe == null || !cachedRecipe.matches(inv, this.level)) {
            RecipeType<ResourceGeneratingRecipe> recipeType = ResourceGeneratingRecipe.Type.INSTANCE;
            cachedRecipe = this.level.getRecipeManager().getRecipeFor(recipeType, inv, this.level).orElse(null);

            if (cachedRecipe == null) {
                List<ResourceGeneratingRecipe> tempRecipes = Arrays.asList(ResourceGeneratingRecipe.Serializer.getRecipes());
                cachedRecipe = tempRecipes.stream().filter(r -> r.matches(inv, this.level)).findFirst().orElse(null);
            }
        }

        return cachedRecipe;
    }

    private SimpleContainer getInputsAsInventory() {
        return new SimpleContainer(this.inputItemHandler.getStackInSlot(0).copy());
    }

    private ItemStack getWorkOutput(@Nullable ResourceGeneratingRecipe recipe) {
        if (recipe != null) {
            return recipe.getResultItem(RegistryAccess.EMPTY).copy();
        }

        return ItemStack.EMPTY;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inputItemHandler.deserializeNBT(tag.getCompound("invIn"));
        outputItemHandler.deserializeNBT(tag.getCompound("invOut"));
        processingTime = tag.getInt("procTime");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("invIn", inputItemHandler.serializeNBT());
        tag.put("invOut", outputItemHandler.serializeNBT());
        tag.putInt("procTime", processingTime);
    }

    private ItemStackHandler createInputHandler() {
        return new ItemStackHandler(INPUT_SLOTS) {

            @Override
            protected void onContentsChanged(int slot) {
                // To make sure the TE persists when the chunk is saved later we need to
                // mark it dirty every time the item handler changes

                ItemStack stack = getStackInSlot(slot);

                /*
                 * The following code automatically converts the old
                 * singularity items (pre-overhaul) to the new ones when
                 * placed in the SRG.
                 *
                 * We need to check if the item is a QuantimSingularityItem
                 * first and skip this if not, otherwise this crashes on
                 * singularities created from JSON recipes (such as when
                 * added by KubeJS)
                 *
                 * - NR
                 */
                if (isItemValid(slot, stack) && 
                        !stack.is(ModItems.QUANTUM_SINGULARITY.get()) &&
                        stack.getItem() instanceof
                        QuantumSingularityItem singularityItem) {
                    ResourceLocation quantumIdentifier = singularityItem.getQuantumIdentifier(stack);

                    ItemStack newStack = new ItemStack(ModItems.QUANTUM_SINGULARITY.get(), stack.getCount(), stack.getOrCreateTag());
                    newStack.getOrCreateTag().putString(QuantumSingularityItem.QUANTUM_ID, quantumIdentifier.toString());
                    setStackInSlot(slot, newStack);
                }

                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.is(ModTags.Items.QUANTUM_SINGULARITIES);
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                // can only insert singularities in the input slot
                if (isItemValid(slot, stack))
                    return super.insertItem(slot, stack, simulate);

                return stack;
            }
        };
    }

    private ItemStackHandler createOutputHandler() {
        return new ItemStackHandler(OUTPUT_SLOTS) {

            @Override
            protected void onContentsChanged(int slot) {
                // To make sure the TE persists when the chunk is saved later we need to
                // mark it dirty every time the item handler changes
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return !stack.is(ModTags.Items.QUANTUM_SINGULARITIES);
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                // can't insert into the output slot
                return stack;
            }
        };
    }

    /**
     * implementing classes should do something like this:
     *
     * return Component.translatable("cuboidmod.container.[identifier]");
     *
     * @return the display name
     */
    public abstract Component getDisplayName();

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            // if side is null, then it's not via automation, so provide access to everything
            if (side == null)
                return combinedHandler.cast();

            // if side is not null, then it's automation - we only want to allow output!
            return outputHandler.cast();
        }

        return super.getCapability(cap, side);
    }

    public abstract AbstractContainerMenu createContainer(int i, Level level, BlockPos pos, Inventory playerInventory, Player playerEntity);

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() { 
        CompoundTag nbtTag = super.getUpdateTag();
        this.saveAdditional(nbtTag);
        this.setChanged();
        return nbtTag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        this.load(tag);
        this.setChanged();
        level.sendBlockUpdated(worldPosition, level.getBlockState(worldPosition).getBlock().defaultBlockState(), level.getBlockState(worldPosition), 2);
    }

    public Container getContentDrops() {
        return getInputsAsInventory();
    }
}
