/**
 * This class is a modification of vazkii.botania.common.block.subtile.functional.SubTileOrechid
 * Additional code was copied and modified from vazkii.botania.common.block.subtile.SubTilePureDaisy
 * Modifications were made by Taw
 * Original header included below
 * ***
 *
 *
 * This class was created by <Vazkii>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 *
 * File Created @ [Mar 11, 2014, 5:40:55 PM (GMT)]
 */
package com.towboat.morechids.block.subtile;

import com.towboat.morechids.tweaker.BlockOutput;
import com.towboat.morechids.tweaker.BlockOutputMapping;
import com.towboat.morechids.tweaker.MorechidDefinition;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vazkii.botania.api.lexicon.LexiconEntry;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.SubTileFunctional;
import vazkii.botania.api.subtile.signature.SubTileSignature;
import vazkii.botania.common.Botania;
import vazkii.botania.common.core.handler.ConfigHandler;
import vazkii.botania.common.core.handler.ModSounds;

import java.util.ArrayList;
import java.util.List;

public class CustomOrechidSubtile extends SubTileFunctional implements SubTileSignature {

    public MorechidDefinition definition;
    public String name;
    private static final String TAG_POSITION = "position";
    private static final String TAG_TICKS_REMAINING = "ticksRemaining";
    private static final int UPDATE_ACTIVE_EVENT = 0;
    private static final int RECIPE_COMPLETE_EVENT = 1;
    private static final int UPDATE_INACTIVE_EVENT = 2;

    private BlockPos[] POSITIONS;
    private int[] ticksRemaining;
    private boolean[] activePositions;
    private BlockOutput[] activeRecipes;

    private int positionAt = 0;

    public CustomOrechidSubtile() {
        super();
    }

    public void init() {
        if (getTimeCost() <= 0) return;
        int range = getRange();
        int rangeY = getRangeY();
        int posCount = range * range * rangeY - 1;
        POSITIONS = new BlockPos[posCount];
        ticksRemaining = new int[posCount];
        activePositions = new boolean[posCount];
        int i = 0;
        for (BlockPos pos : BlockPos.getAllInBox(getPos().add(-range, -rangeY, -range), getPos().add(range, rangeY, range))) {
            if (pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0) continue;
            POSITIONS[i] = pos;
            ticksRemaining[i] = -1;
            activePositions[i] = false;
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if(supertile.getWorld().isRemote || redstoneSignal > 0 || !canOperate())
            return;

        int timeCost = getTimeCost();
        int manaCost = getManaCost();

        if (timeCost > 0) {
            if (getWorld().isRemote) {
                for (int i = 0; i < POSITIONS.length; i++) {
                    if (activePositions[i]) {
                        BlockPos coords = POSITIONS[i];
                        Botania.proxy.sparkleFX(coords.getX() + Math.random(), coords.getY() + Math.random(), coords.getZ() + Math.random(),
                                ((getColor() >>> 16) & 0xFF)/255F, ((getColor() >>> 8) & 0xFF)/255F, (getColor() & 0xFF)/255F, (float) Math.random(), 5);
                    }
                }

                return;
            }

            positionAt++;
            if (positionAt == POSITIONS.length) positionAt = 0;

            BlockPos coords = POSITIONS[positionAt];
            World world = super.getWorld();

            if (!world.isAirBlock(coords)) {
                IBlockState state = world.getBlockState(coords);
                if (definition.matches(state)) {
                    BlockOutput output = activeRecipes[positionAt];
                    if (output == null) {
                        output = definition.recipes.get(state).selectBlockOutput();
                    }
                    if (ticksRemaining[positionAt] == -1) {
                        ticksRemaining[positionAt] = getTimeCost();
                    }
                    ticksRemaining[positionAt]--;

                    if (ticksRemaining[positionAt] <= 0) {
                        ticksRemaining[positionAt] = -1;
                        if (!world.isRemote) {
                            world.setBlockState(coords, output.selectBlock());
                        }
                        world.addBlockEvent(getPos(), supertile.getBlockType(), RECIPE_COMPLETE_EVENT, positionAt);
                        if (definition.blockBreakParticles) {
                            supertile.getWorld().playEvent(2001, coords, Block.getStateId(recipe.getOutputState()));
                        }
                    }
                } else {
                    ticksRemaining[positionAt] = -1;
                    activeRecipes[positionAt] = null;
                }
            } else {
                ticksRemaining[positionAt] = -1;
                activeRecipes[positionAt] = null;
            }
        }

        if(mana >= manaCost && ticksExisted % getDelay() == 0) {
            BlockPos coords = getCoordsToPut();
            if(coords != null) {
                ItemStack stack = getOreToPut(supertile.getWorld().getBlockState(coords));
                if(!stack.isEmpty()) {
                    Block block = Block.getBlockFromItem(stack.getItem());
                    int meta = stack.getItemDamage();
                    supertile.getWorld().setBlockState(coords, block.getStateFromMeta(meta), 1 | 2);
                    if(ConfigHandler.blockBreakParticles)
                        supertile.getWorld().playEvent(2001, coords, Block.getIdFromBlock(block) + (meta << 12));
                    if (Botania.gardenOfGlassLoaded ? definition.playSoundGOG : definition.playSound) {
                        supertile.getWorld().playSound(null, supertile.getPos(), ModSounds.orechid, SoundCategory.BLOCKS, 2F, 1F);
                    }

                    mana -= manaCost;
                    sync();
                }
            }
        }
    }

    private void updateActivePositions() {
        boolean changed = false;
        for (int i = 0; i < ticksRemaining.length; i++) {
            if (ticksRemaining[i] > -1) {
                if (!activePositions[i])
                    getWorld().addBlockEvent(getPos(), supertile.getBlockType(), UPDATE_ACTIVE_EVENT, i);
            } else if (activePositions[i]) {
                getWorld().addBlockEvent(getPos(), supertile.getBlockType(), UPDATE_INACTIVE_EVENT, i);
            }
        }
    }

    @Override
    public boolean receiveClientEvent(int type, int param) {
        switch (type) {
            case UPDATE_ACTIVE_EVENT:
                activePositions[param] = true;
                return true;
            case UPDATE_INACTIVE_EVENT:
                activePositions[param] = false;
                return true;
            case RECIPE_COMPLETE_EVENT:
                if (getWorld().isRemote) {
                    BlockPos coords = getPos().add(POSITIONS[param]);
                    for(int i = 0; i < 25; i++) {
                        double x = coords.getX() + Math.random();
                        double y = coords.getY() + Math.random() + 0.5;
                        double z = coords.getZ() + Math.random();

                        Botania.proxy.wispFX(x, y, z,((getColor() >>> 16) & 0xFF)/255F, ((getColor() >>> 8) & 0xFF)/255F,
                                (getColor() & 0xFF)/255F, (float) Math.random() / 2F);
                    }
                }
                return true;
            default:
                return super.receiveClientEvent(type, param);
        }
    }

    public ItemStack getOreToPut(IBlockState block) {
        BlockOutputMapping mapping = definition.recipes.get(block);
        if (mapping == null) {
            mapping = definition.recipes.get(block.getBlock());
            if (mapping == null) {
                return ItemStack.EMPTY;
            }
        }

        IBlockState state = mapping.selectBlock(supertile.getWorld().rand);
        if (state == null) {
            return ItemStack.EMPTY;
        }
        Block b = state.getBlock();
        if (b == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(state.getBlock());
    }

    private BlockPos getCoordsToPut() {
        List<BlockPos> possibleCoords = new ArrayList<>();

        int rangeX = getRange();
        int rangeY = getRangeY();

        for (BlockPos pos : BlockPos.getAllInBox(getPos().add(-rangeX, -rangeY, -rangeX), getPos().add(rangeX, rangeY, rangeX))) {
            IBlockState state = supertile.getWorld().getBlockState(pos);
            if (definition.matches(state))
                possibleCoords.add(pos);
        }

        if (possibleCoords.isEmpty())
            return null;
        return possibleCoords.get(supertile.getWorld().rand.nextInt(possibleCoords.size()));
    }



    public boolean canOperate() {
        return true;
    }

    public int getManaCost() {
        return Botania.gardenOfGlassLoaded ? definition.manaCostGOG : definition.manaCost;
    }

    public int getTimeCost() {
        return Botania.gardenOfGlassLoaded ? definition.timeCostGOG : definition.timeCost;
    }

    public int getDelay() {
        return Botania.gardenOfGlassLoaded ? definition.delayGOG : definition.delay;
    }

    public int getRange() {
        return Botania.gardenOfGlassLoaded ? definition.rangeGOG : definition.range;
    }

    public int getRangeY() {
        return Botania.gardenOfGlassLoaded ? definition.rangeYGOG : definition.rangeY;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return new RadiusDescriptor.Square(toBlockPos(), getRange());
    }

    @Override
    public boolean acceptsRedstone() {
        return true;
    }

    @Override
    public int getColor() {
        return Botania.gardenOfGlassLoaded ? definition.particleColorGOG : definition.particleColor;
    }

    @Override
    public int getMaxMana() {
        return getManaCost();
    }

    @Override
    public LexiconEntry getEntry() {
        return null;
    }

    public String getIdentifier() {
        return definition.getIdentifier();
    }
    @Override
    public String getUnlocalizedNameForStack(ItemStack itemStack) {
        return "morechids:" + getIdentifier();
    }

    @Override
    public String getUnlocalizedLoreTextForStack(ItemStack itemStack) {
        return "morechids:" + getIdentifier() + ".reference";
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addTooltip(ItemStack stack, World world, List<String> tooltip) {
        if(getManaCost() == 0)
            tooltip.add(TextFormatting.BLUE + I18n.translateToLocal("botania.flowerType.special"));
        else
            tooltip.add(TextFormatting.BLUE + I18n.translateToLocal("botania.flowerType.functional"));
    }
}