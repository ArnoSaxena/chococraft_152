// <copyright file="EntityChocoboRideable.java">
// Copyright (c) 2012 All Right Reserved, http://chococraft.arno-saxena.de/
//
// THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY 
// KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// </copyright>
// <author>Arno Saxena</author>
// <email>al-s@gmx.de</email>
// <date>2012-09-10</date>
// <summary>Abstract entity class for everything connected to the Chocobo as a mount</summary>

package chococraft.common.entities;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import chococraft.common.Constants;
import chococraft.common.ModChocoCraft;
import chococraft.common.bags.ChocoBagInventory;
import chococraft.common.bags.ChocoPackBagInventory;
import chococraft.common.bags.ChocoSaddleBagInventory;
import chococraft.common.network.serverSide.PacketChocoboDropGear;
import chococraft.common.network.serverSide.PacketChocoboMount;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import cpw.mods.fml.common.network.PacketDispatcher;


public abstract class EntityChocoboRideable extends EntityAnimalChocobo
{
	protected double prevMotionX;
	protected double prevMotionZ;
	protected boolean shouldSteer;    
	protected boolean flying;
	protected boolean isHighJumping;

	protected ChocoBagInventory bagsInventory;    
	protected ChocoboRiderList riderList;

	public EntityChocoboRideable(World world)
	{
		super(world);
		this.ignoreFrustumCheck = true;
		this.riderList = new ChocoboRiderList();
	}
	
	
	abstract public void setStepHeight(boolean mounted);
	abstract public void setLandMovementFactor(boolean mounted);
	abstract public void setJumpHigh(boolean mounted);
	abstract public void setRiderAbilities(boolean mounted);
	
    @Override
	protected void entityInit()
	{
		super.entityInit();
		this.dataWatcher.addObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)0));
	}

    @Override
	public boolean isAIEnabled()
	{
		return false;
	}

    @Override
	public void writeEntityToNBT(NBTTagCompound nbttagcompound)
	{
		super.writeEntityToNBT(nbttagcompound);
		nbttagcompound.setBoolean("Saddle", this.isSaddled());
		nbttagcompound.setBoolean("SaddleBag", this.isSaddleBagged());
		nbttagcompound.setBoolean("PackBag", this.isPackBagged());
		if(null != this.bagsInventory)
		{
			nbttagcompound.setTag("SaddleBagInventory", this.bagsInventory.writeToNBT(new NBTTagList()));
		}
		if(null != this.riderList)
		{
			nbttagcompound.setTag("riderList", this.riderList.writeToNBT(new NBTTagList()));
		}
	}

    @Override
	public void readEntityFromNBT(NBTTagCompound nbttagcompound)
	{
		super.readEntityFromNBT(nbttagcompound);
		this.setSaddled(nbttagcompound.getBoolean("Saddle"));
		this.setSaddleBagged(nbttagcompound.getBoolean("SaddleBag"));
		this.setPackBagged(nbttagcompound.getBoolean("PackBag"));
		this.setWander(!this.isFollowing() && !this.isSaddled() && !this.isPackBagged());
		if(nbttagcompound.hasKey("SaddleBagInventory") && null != this.bagsInventory)
		{
			this.bagsInventory.readFromNBT(nbttagcompound.getTagList("SaddleBagInventory"));
		}
		if(nbttagcompound.hasKey("riderList") && null != this.riderList)
		{
			this.riderList.readFromNBT(nbttagcompound.getTagList("riderList"));
		}
	}    

    @Override
	public void writeSpawnData(ByteArrayDataOutput data)
	{
		super.writeSpawnData(data);
		data.writeBoolean(this.isSaddled());
		data.writeBoolean(this.isSaddleBagged());
		data.writeBoolean(this.isPackBagged());
	}

    @Override
	public void readSpawnData(ByteArrayDataInput data)
	{
		super.readSpawnData(data);
		this.setSaddled(data.readBoolean());
		this.setSaddleBagged(data.readBoolean());
		this.setPackBagged(data.readBoolean());
	}

    @Override
	public void onLivingUpdate()
	{
		if (this.riddenByEntity != null)
		{
			this.setRotationYawAndPitch();

			List<?> list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(0.21D, 0.0D, 0.21D));
			if (list != null && list.size() > 0)
			{
				for (int i = 0; i < list.size(); i++)
				{
					Entity entity = (Entity)list.get(i);
					if (entity instanceof IMob && entity.canBePushed() && entity != this.riddenByEntity)
					{
						entity.applyEntityCollision(this);
						//this.trample(entity);
					}
				}
			}
			this.setRiderAbilities(true);
		}
		super.onLivingUpdate();
	}

    @Override
	public void onDeath(DamageSource damageSource)
	{
		if(this.isServer())
		{
			if(this.isSaddled())
			{
				this.dropItem(ModChocoCraft.chocoboSaddleItem.itemID, 1);
			}
			
			if(this.isSaddleBagged())
			{
				this.dropItem(ModChocoCraft.chocoboSaddleBagsItem.itemID, 1);
				this.bagsInventory.dropAllItems();
			}
			
			if(this.isPackBagged())
			{
				this.dropItem(ModChocoCraft.chocoboPackBagsItem.itemID, 1);
				this.bagsInventory.dropAllItems();
			}
		}
		super.onDeath(damageSource);
	}

    @Override
	public boolean interact(EntityPlayer entityplayer)
	{
		boolean interacted = false;
		interacted = super.interact(entityplayer);
		if(!interacted)
		{
			// if Chocobo has saddlebag shift-click will allways open it 
			if(entityplayer.isSneaking() && (this.isSaddleBagged() || this.isPackBagged())){
				return this.onOpenSaddlePackBagInteraction(entityplayer);
			}

			ItemStack itemstack = entityplayer.inventory.getCurrentItem();
			if(itemstack != null)
			{
				if (itemstack.itemID == ModChocoCraft.chocoboSaddleItem.itemID)
				{
					this.onSaddleUse(entityplayer);
					interacted = true;
				}
				else if (itemstack.itemID == ModChocoCraft.chocoboSaddleBagsItem.itemID)
				{
					this.onSaddleBagsUse(entityplayer);
					interacted = true;
				}
				else if (itemstack.itemID == ModChocoCraft.chocoboPackBagsItem.itemID)
				{
					this.onPackBagsUse(entityplayer);
					interacted = true;
				}
				else if (itemstack.itemID == ModChocoCraft.chocoboWhistleItem.itemID)
				{
					this.onWhistleUse();
					interacted = true;
				}
			}
		}
		return interacted;
	}

	public void onSaddleUse(EntityPlayer entityplayer)
	{
		if (this.isTamed() && !this.isSaddled() && !this.isPackBagged())
		{
			this.setSaddled(true);
			this.setWander(false);
			this.setFollowing(false);
			this.useItem(entityplayer);
		}
	}

	public void onPackBagsUse(EntityPlayer entityplayer)
	{
		if(this.isTamed() && !this.isSaddled() && !this.isPackBagged())
		{
			this.setPackBagged(true);
			this.setWander(false);
			this.setFollowing(true);
			this.useItem(entityplayer);
		}
	}

	public void onSaddleBagsUse(EntityPlayer entityplayer)
	{
		if (this.isTamed() && this.isSaddled() && !this.isSaddleBagged())
		{
			this.setSaddleBagged(true);
			this.useItem(entityplayer);
		}
	}

	public void onWhistleUse()
	{
		// whistle no longer for untaming but for teleporting last ridden chocobo to player...
		// also this should go to EntityChocoboRideable
	}

	protected boolean onOpenSaddlePackBagInteraction(EntityPlayer entityplayer)
	{
		boolean interacted = false;

		if(entityplayer.isSneaking() && this.isSaddleBagged())
		{
			entityplayer.openGui(ModChocoCraft.instance, 0, worldObj, this.entityId, 0, 0);
			interacted = true;
		}
		else if (entityplayer.isSneaking() && this.isPackBagged())
		{
			entityplayer.openGui(ModChocoCraft.instance, 0, worldObj, this.entityId, 1, 0);
			interacted = true;
		}
		return interacted;
	}

	protected boolean onEmptyHandInteraction(EntityPlayer entityplayer)
	{
		boolean interacted = false;
		interacted = super.onEmptyHandInteraction(entityplayer);
		
		if(this.isSaddled())
		{
			if (this.riddenByEntity == null || this.riddenByEntity == entityplayer)
			{
				if(this.isClient())
				{
					this.sendMountUpdate();
					if (this.riddenByEntity == null)
					{
						this.dismountChocobo(entityplayer);
					}
					else
					{
						this.mountChocobo(entityplayer);
					}
				}

				interacted = true;
			}
		}
		return interacted;
	}

	protected void setRotationYawAndPitch()
	{
		if (this.riddenByEntity != null && this.riddenByEntity instanceof EntityPlayer)
		{
			if(this.isServer())
			{
				this.rotationPitch = 0.0F;
				EntityPlayer rider = (EntityPlayer)this.riddenByEntity;
				this.rotationYaw = rider.rotationYaw;
				this.prevRotationYaw = rider.rotationYaw;
				this.setRotation(this.rotationYaw, this.rotationPitch);
			}
		}
	}

	/**
	 * Tries to moves the entity by the passed in displacement. Args: x, y, z
	 */
	@Override
	public void moveEntity(double moveX, double moveY, double moveZ)
	{
		if (this.riddenByEntity != null)
		{
			this.sendRiderJumpUpdate();

			this.setRotationYawAndPitch();
			
			if(this.isServer())
			{
				
				if (this.onGround)
				{
					this.motionX = this.riddenByEntity.motionX * this.landSpeedFactor;
					this.motionZ = this.riddenByEntity.motionZ * this.landSpeedFactor;
					this.isInWeb = false;
				}
				else if (this.isAirBorne)
				{
					this.motionX = this.riddenByEntity.motionX * this.airbornSpeedFactor;
					this.motionZ = this.riddenByEntity.motionZ * this.airbornSpeedFactor;
				}
				else if (this.inWater)
				{
					this.motionX = this.riddenByEntity.motionX * this.waterSpeedFactor;
					this.motionZ = this.riddenByEntity.motionZ * this.waterSpeedFactor;					
				}
				else
				{
					this.motionX = this.riddenByEntity.motionX * this.landSpeedFactor;
					this.motionZ = this.riddenByEntity.motionZ * this.landSpeedFactor;
				}

				this.prevMotionX = this.motionX;
				this.prevMotionZ = this.motionZ;
			}

			if(this.riddenByEntity instanceof EntityPlayer)
			{    				
				EntityPlayer entityplayer = (EntityPlayer)this.riddenByEntity;

				if (entityplayer.isJumping)
				{
					if (this.canFly)
					{
						this.motionY += 0.1D;
						this.setFlying(true);
					}
					else if (this.canJumpHigh && !this.isHighJumping)
					{
						this.motionY += 0.4D;
						this.isHighJumping = true;
					}
				}

				if (entityplayer.isSneaking())
				{
					if(this.canFly)
					{
						this.motionY -= 0.15D;
					}

					if(this.inWater && this.canBreatheUnderwater())
					{
						this.motionY -= 0.15D;
					}
				}
			}

			super.moveEntity(this.motionX, this.motionY, this.motionZ);
		}
		else
		{
			super.moveEntity(moveX, moveY, moveZ);
		}
	}

    @Override
	public void onUpdate()
	{
		super.onUpdate();
	}

	public void setFlying(Boolean flying)
	{
		this.flying = flying;
	}

	public Boolean isFlying()
	{
		return this.flying;
	}

	@Override
	public void updateRiderPosition()
	{
		if (this.riddenByEntity != null)
		{
			double deltaPosX = Math.cos((double)this.rotationYaw * Math.PI / 180.0D) * 0.4D;
			double deltaPosZ = Math.sin((double)this.rotationYaw * Math.PI / 180.0D) * 0.4D;
			this.riddenByEntity.setPosition(this.posX + deltaPosX, this.posY + this.getMountedYOffset() + this.riddenByEntity.getYOffset(), this.posZ + deltaPosZ);
		}
	}   

	//    public void trample(Entity entity)
	//    {
		//    	if(entity instanceof EntityAnimalChoco)
	//    	{
	//    		return;
	//    	}
	//    	
	//        double addVX = -MathHelper.sin((rotationYaw * (float)Math.PI) / 180F);
	//        double addVY = 0.3D;
	//        double addVZ = MathHelper.cos((rotationYaw * (float)Math.PI) / 180F);
	//        double vFactor = 0.5D;
	//
	//        if ((entity instanceof EntityMob) && ChocoboHelper.isHostile(entity))
	//        {
	//            this.attackEntityAsMob(entity);
	//            vFactor = 1.5D;
	//        }
	//        entity.addVelocity(addVX, addVY, addVZ);
	//        entity.motionX *= vFactor;
	//        entity.motionZ *= vFactor;
	//    }

	/**
	 * Called when the entity is attacked.
	 */
    @Override
	public boolean attackEntityFrom(DamageSource damagesource, int i)
	{
		if(null != damagesource)
		{
			Entity entity = damagesource.getEntity();
			if (entity != null && entity == this.riddenByEntity)
			{
				return false;
			}
			else
			{
				return super.attackEntityFrom(damagesource, i);
			}
		}
		else
		{
			return false;
		}
	}

    @Override
	public double getMountedYOffset()
	{
		return 1.4D;
	}

	public boolean isSaddled()
	{
		return (this.dataWatcher.getWatchableObjectByte(Constants.DW_ID_ECR_FLAGS) & Constants.DW_VAL_ECR_SADDLED_ON) != 0;
	}

	public void setSaddled(boolean saddled)
	{
		byte ecrFlags = this.dataWatcher.getWatchableObjectByte(Constants.DW_ID_ECR_FLAGS);

		if (saddled)
		{
			this.dataWatcher.updateObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)(ecrFlags | Constants.DW_VAL_ECR_SADDLED_ON)));
		}
		else
		{
			this.dataWatcher.updateObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)(ecrFlags & Constants.DW_VAL_ECR_SADDLED_OFF)));
		}
		this.texture = this.getEntityTexture();
	}
	
	@Override
	public void toggleFollowWanderStay()
	{
		if(!ModChocoCraft.saddledCanWander && (this.isSaddled() || this.isPackBagged()))
		{
			this.toggleFollowStay();
		}
		else
		{
			super.toggleFollowWanderStay();
		}
	}

	
	// Chocobo as inventory
	public IInventory getIInventory()
	{
		return (IInventory)this.bagsInventory;
	}
	
	public ChocoBagInventory getChocoBagInventory()
	{
		return this.bagsInventory;
	}

	public void injectInventory(ChocoBagInventory inventory)
	{
		if(inventory != null)
		{
			this.bagsInventory = inventory;
		}
	}	
	
	public Boolean isSaddleBagged()
	{
		return (this.dataWatcher.getWatchableObjectByte(Constants.DW_ID_ECR_FLAGS) & Constants.DW_VAL_ECR_SADDLEBAGGED_ON) != 0;
	}

	public void setSaddleBagged(boolean saddleBags)
	{
		if(saddleBags != this.isSaddleBagged() || this.bagsInventory == null)
		{
			byte ecrFlags = this.dataWatcher.getWatchableObjectByte(Constants.DW_ID_ECR_FLAGS);
			if (saddleBags)
			{
				this.bagsInventory = new ChocoSaddleBagInventory(this);
				this.dataWatcher.updateObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)(ecrFlags | Constants.DW_VAL_ECR_SADDLEBAGGED_ON)));
			}
			else
			{
				this.bagsInventory = null;
				this.dataWatcher.updateObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)(ecrFlags & Constants.DW_VAL_ECR_SADDLEBAGGED_OFF)));
			}
			this.texture = this.getEntityTexture();
		}    	
	}

	public Boolean isPackBagged()
	{
		return (this.dataWatcher.getWatchableObjectByte(Constants.DW_ID_ECR_FLAGS) & Constants.DW_VAL_ECR_PACKBAGGED_ON) != 0;
	}

	public void setPackBagged(boolean packBagged)
	{
		if(packBagged != this.isPackBagged() || this.bagsInventory == null)
		{
			byte ecrFlags = this.dataWatcher.getWatchableObjectByte(Constants.DW_ID_ECR_FLAGS);
			if (packBagged)
			{
				this.bagsInventory = new ChocoPackBagInventory(this);
				this.dataWatcher.updateObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)(ecrFlags | Constants.DW_VAL_ECR_PACKBAGGED_ON)));
			}
			else
			{
				this.bagsInventory = null;
				this.dataWatcher.updateObject(Constants.DW_ID_ECR_FLAGS, Byte.valueOf((byte)(ecrFlags & Constants.DW_VAL_ECR_PACKBAGGED_OFF)));
			}
			this.texture = this.getEntityTexture();
		}
	}

    @Override
	protected boolean canDespawn()
	{
		return super.canDespawn() && !isSaddled();
	}

	public void mountChocobo(EntityPlayer player)
	{
		player.setSprinting(false);
		this.setStepHeight(true);
		this.setJumpHigh(true);
		this.setLandMovementFactor(true);
		player.mountEntity(this);
	}

	public void dismountChocobo(EntityPlayer player)
	{
		this.isJumping = false;
		this.setStepHeight(false);
		this.setJumpHigh(false);
		this.setLandMovementFactor(false);
		player.mountEntity(null);
	}
	
	public void sendMountUpdate()
	{
		if(this.isClient())
		{
			PacketChocoboMount packet = new PacketChocoboMount(this);
			PacketDispatcher.sendPacketToServer(packet.getPacket());
		}
	}
	
	public void sendDropSaddleAndBags()
	{
		if(this.isClient())
		{
			PacketChocoboDropGear packet = new PacketChocoboDropGear(this);
			PacketDispatcher.sendPacketToServer(packet.getPacket());
			this.setSaddleBagged(false);
			this.setSaddled(false);
			this.setPackBagged(false);
		}
	}
	
	@Override
    public boolean shouldRiderFaceForward(EntityPlayer player)
    {
    	return true;
    }
}
