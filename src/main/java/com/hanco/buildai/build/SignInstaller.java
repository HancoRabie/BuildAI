package com.hanco.buildai.build;

import com.hanco.buildai.blueprint.SignTextPlan;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Literal only: no commands, components, hover/click payloads or raw NBT. */
public final class SignInstaller {
    private SignInstaller(){}
    public static boolean install(ServerWorld world,BlockPos at,SignTextPlan.Entry entry){
        if(!(world.getBlockEntity(at) instanceof SignBlockEntity tile))
            return false;
        BlockState state=world.getBlockState(at);
        if(!SignTextPlan.isSign(net.minecraft.registry.Registries.BLOCK
                .getId(state.getBlock()).toString()))return false;
        SignText front=tile.getFrontText(),back=tile.getBackText();
        for(int line=0;line<4;line++){
            front=front.withMessage(line,Text.literal(entry.front().get(line)));
            back=back.withMessage(line,Text.literal(entry.back().get(line)));
        }
        tile.setText(front,true);
        tile.setText(back,false);
        tile.markDirty();
        world.updateListeners(at,state,state,net.minecraft.block.Block.NOTIFY_LISTENERS);
        return true;
    }
}
