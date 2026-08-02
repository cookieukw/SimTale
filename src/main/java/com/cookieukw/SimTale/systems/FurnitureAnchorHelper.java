package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3i;

/**
 * Descobre qual bloco e a ANCORA de um movel que ocupa varios blocos.
 *
 * <h3>O problema que isto resolve</h3>
 * Moveis no Hytale nao ocupam um bloco. Uma cama ocupa <b>seis</b>. O jogo representa isso com
 * um bloco ancora, que carrega o movel de verdade, mais blocos de <i>filler</i> que apenas
 * marcam o espaco ocupado.
 *
 * <p>O SimTale ignorava essa distincao e tratava cada bloco como um movel independente. O
 * sintoma direto aparecia no {@code /simtale debugnear}: duas camas fisicas viravam
 * <b>doze</b> camas registradas.
 *
 * <p>O estrago real era no sono. {@code BlockMountAPI.mountOnBlock} calcula onde o corpo deita
 * a partir do ponto de montagem declarado no asset — para a cama do vilarejo,
 * {@code Beds: [{Offset {X:0.4, Y:0.4, Z:1.0}}]} — e esse offset e medido <b>a partir do bloco
 * ancora</b>. Passando um filler, o corpo sai deslocado exatamente pela distancia daquele filler
 * ate a ancora.
 *
 * <p>Isso explica por que a NPC ficava sempre "quase" no lugar certo, e por que nenhuma
 * compensacao por posicao resolvia: o erro nao era constante, mudava conforme qual dos seis
 * blocos tinha sido sorteado.
 *
 * <h3>Como o motor marca isso</h3>
 * Cada filler guarda um inteiro com o deslocamento ate sua ancora. O proprio jogo tem um
 * {@code /inspectfiller} que le exatamente isso, via
 * {@code BlockSection.getFiller(x,y,z)} + {@code FillerBlockUtil.unpackX/Y/Z}. As portas ja
 * usavam o mesmo mecanismo — {@code DoorInteraction.DoorInfo} carrega um campo {@code filler}.
 */
public final class FurnitureAnchorHelper {

    private static final SimLog LOGGER = SimLog.forClass(FurnitureAnchorHelper.class);

    private FurnitureAnchorHelper() {
        // Classe utilitaria.
    }

    /**
     * Devolve a ancora do movel que ocupa {@code pos}, ou o proprio {@code pos} quando ele ja e a
     * ancora (ou quando nao da para determinar).
     *
     * <p>Nunca devolve null: na duvida, devolve a entrada. Assim quem chama nao precisa tratar
     * caso especial, e o comportamento no pior cenario e o de antes desta classe existir.
     */
    public static Vector3i anchorOf(World world, int x, int y, int z) {
        Vector3i entrada = new Vector3i(x, y, z);
        if (world == null) return entrada;

        try {
            int filler = readFiller(world, x, y, z);
            if (filler == 0) {
                // Zero significa "nao sou filler" — este bloco ja e a ancora.
                return entrada;
            }

            int dx = FillerBlockUtil.unpackX(filler);
            int dy = FillerBlockUtil.unpackY(filler);
            int dz = FillerBlockUtil.unpackZ(filler);

            // O sinal do deslocamento nao esta documentado, e chutar errado apontaria para o
            // lado oposto do movel. Em vez de assumir, testa-se os dois sentidos e aceita-se o
            // que realmente parece uma ancora: mesmo tipo de bloco e filler zerado.
            Vector3i menos = new Vector3i(x - dx, y - dy, z - dz);
            if (looksLikeAnchor(world, menos, x, y, z)) return menos;

            Vector3i mais = new Vector3i(x + dx, y + dy, z + dz);
            if (looksLikeAnchor(world, mais, x, y, z)) return mais;

            LOGGER.debug("[SimTale] filler em ({},{},{}) = {} nao levou a uma ancora valida; usando o proprio bloco.",
                    x, y, z, filler);
            return entrada;
        } catch (Exception e) {
            // Chunk descarregado, secao ausente, API divergente: seguir com o bloco original e
            // sempre melhor do que abortar o sono da NPC.
            LOGGER.debug("[SimTale] falha ao resolver ancora de ({},{},{}): {}", x, y, z, e.toString());
            return entrada;
        }
    }

    /** Conveniencia para quem ja tem um {@link Vector3i}. */
    public static Vector3i anchorOf(World world, Vector3i pos) {
        return pos == null ? null : anchorOf(world, pos.x, pos.y, pos.z);
    }

    /** True quando {@code pos} e um bloco do mesmo movel e nao aponta para outra ancora. */
    private static boolean looksLikeAnchor(World world, Vector3i pos, int origemX, int origemY, int origemZ) {
        if (pos.x == origemX && pos.y == origemY && pos.z == origemZ) return false;

        BlockType tipo = world.getBlockType(pos.x, pos.y, pos.z);
        if (tipo == null || tipo.getId() == null) return false;

        BlockType origem = world.getBlockType(origemX, origemY, origemZ);
        if (origem == null || origem.getId() == null) return false;

        // Mesmo id: os blocos de um movel compartilham o tipo. Isso evita aceitar um bloco
        // qualquer que por acaso esteja no deslocamento calculado.
        if (!tipo.getId().equals(origem.getId())) return false;

        // A ancora nao e filler de ninguem.
        return readFiller(world, pos.x, pos.y, pos.z) == 0;
    }

    /**
     * Le o inteiro de filler de um bloco.
     * <p>
     * Caminho copiado do {@code /inspectfiller} do proprio jogo: a informacao vive na
     * {@code BlockSection}, nao no {@code World}, entao e preciso descer ate a secao do chunk.
     * A variante sincrona {@code getChunkSectionReferenceAtBlock} evita lidar com
     * {@code CompletableFuture} no meio do tick.
     */
    private static int readFiller(World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null) return 0;

        Ref<ChunkStore> secaoRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (secaoRef == null || !secaoRef.isValid()) return 0;

        BlockSection secao = secaoRef.getStore().getComponent(secaoRef, BlockSection.getComponentType());
        if (secao == null) return 0;

        return secao.getFiller(x, y, z);
    }
}
