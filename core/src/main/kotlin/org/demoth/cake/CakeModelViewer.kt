package org.demoth.cake

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.UBJsonReader
import ktx.graphics.use
import java.lang.Float.intBitsToFloat
import java.nio.ByteBuffer
import java.nio.ByteOrder


/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms.  */
class CakeModelViewer : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private lateinit var image: Texture
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: Camera
    private lateinit var models: MutableList<ModelInstance>
    private lateinit var environment: Environment

    override fun create() {
        batch = SpriteBatch()
        image = Texture("libgdx.png")
        modelBatch = ModelBatch()
        camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(0f, 0f, -5f);
        camera.lookAt(0f, 0f, 0f);
        camera.near = 0.1f;
        camera.far = 300f;

        Gdx.input.inputProcessor = CameraInputController(camera)
        modelBatch = ModelBatch()
        models = mutableListOf(
//            ModelInstance(createModel()),
//            loadObjModel(),
//            loadG3dModel(),
//            loadMeshModel(),
            loadMd2Model()
        )

        environment = Environment()
//        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
    }

    /**
     * Create a model programmatically using the model builder
     */
    private fun createModel(): Model {
        val modelBuilder = ModelBuilder()
        val box = modelBuilder.createBox(
            2f, 2f, 2f,
            Material(
                ColorAttribute.createDiffuse(Color.BLUE)
//                todo: add texture and check coords
            ),
            (Usage.Position or Usage.Normal).toLong()
        )
        return box
    }

    /**
     * Load an .obj model. todo: try using a material
     */
    private fun loadObjModel(): ModelInstance {
        val suzanneModel = ObjLoader().loadModel(Gdx.files.internal("suzanne.obj"))
        val suzanne = ModelInstance(suzanneModel)
        suzanne.transform.translate(0f, 2f, 0f)
        return suzanne
    }

    /**
     * Example of building the model with the mesh builder class (from geometric shapes)
     */
    private fun loadMeshModel(): ModelInstance {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        var meshBuilder =
            modelBuilder.part("part1", GL_TRIANGLES, (Usage.Position or Usage.Normal).toLong(), Material())
        meshBuilder.cone(5f, 5f, 5f, 10)
        val node: Node = modelBuilder.node()
        node.translation.set(10f, 0f, 0f)
        meshBuilder = modelBuilder.part("part2", GL_TRIANGLES, (Usage.Position or Usage.Normal).toLong(), Material())
        meshBuilder.sphere(5f, 5f, 5f, 10, 10)
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    /**
     * Load an .g3d model, created with the fbx-conv app from an .fbx file
     */
    private fun loadG3dModel(): ModelInstance {
        val crateModel = G3dModelLoader(UBJsonReader()).loadModel(Gdx.files.internal("crate-wooden.g3db"))
        val crateInstance = ModelInstance(crateModel)
        crateInstance.transform.translate(0f, -2f, 0f)
        crateInstance.transform.scale(0.01f, 0.01f, 0.01f)
        return crateInstance
    }

    private fun loadMd2Model(): ModelInstance {
        val md2Model: Md2Model = readMd2Model()

        // put all vertices of the 1st frame into the buffer
        val (vertexIndices, vertexBuffer) = createVertexIndices(md2Model, 1)

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val meshBuilder = modelBuilder.part(
            "part1",
            GL_TRIANGLES,
            VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)),
            Material(
//                ColorAttribute.createDiffuse(Color.GREEN),
                TextureAttribute(TextureAttribute.Diffuse, Texture(Gdx.files.internal("pain.png")))
            )
        )
        meshBuilder.addMesh(vertexBuffer, vertexIndices)
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    internal data class VertexInfo(val positionIndex: Int, val s: Float, val t: Float)
    internal data class VertexFullInfo(val x: Float, val y: Float, val z: Float, val s: Float, val t: Float)

    private fun convert(info: VertexInfo, positions: Array<Point>, frame: Md2Frame): VertexFullInfo {
        val position = positions[info.positionIndex]
        return VertexFullInfo(
            x = position.x * frame.scale[0],
            y = position.y * frame.scale[1],
            z = position.z * frame.scale[2],
            s = info.s,
            t = info.t
        )

    }

    private fun createVertexIndices(model: Md2Model, frameIndex: Int): Pair<ShortArray, FloatArray> {
        var glCmdIndex = 0 // todo: use queue to pop elements instead of using mutable index?

        val frame = model.frames[frameIndex]!!
        val vertexPositions = frame.points

        val result = mutableListOf<VertexFullInfo>()

        while (true) {
            val numOfPoints = model.glCmds[glCmdIndex]
            glCmdIndex++
            glCmdIndex += if (numOfPoints == 0) {
                break
            } else if (numOfPoints >= 0) {
                // triangle strip
                val vertices = mutableListOf<VertexInfo>()
                for (i in glCmdIndex until (glCmdIndex + numOfPoints * 3) step 3) {
                    val s = intBitsToFloat(model.glCmds[i + 0])
                    val t = intBitsToFloat(model.glCmds[i + 1])
                    val vertexIndex = model.glCmds[i + 2]
                    vertices.add(VertexInfo(vertexIndex, s ,t))
                }
                // converting strips into separate triangles
                var clockwise = false // when converting a triangle strip into a set of separate triangles, need to alternate the winding direction
                vertices.windowed(3).forEach {
                    if (clockwise) {
                        result.add(convert(it[0], vertexPositions, frame))
                        result.add(convert(it[1], vertexPositions, frame))
                        result.add(convert(it[2], vertexPositions, frame))
                    } else {
                        result.add(convert(it[2], vertexPositions, frame))
                        result.add(convert(it[1], vertexPositions, frame))
                        result.add(convert(it[0], vertexPositions, frame))
                    }
                    clockwise = !clockwise
                }
                numOfPoints * 3
            } else {
                // triangle fan
                val vertices = mutableListOf<VertexInfo>()
                for (i in glCmdIndex until (glCmdIndex - numOfPoints * 3) step 3) {
                    val s = intBitsToFloat(model.glCmds[i + 0])
                    val t = intBitsToFloat(model.glCmds[i + 1])
                    val vertexIndex = model.glCmds[i + 2]
                    vertices.add(VertexInfo(vertexIndex, s, t))
                }
                convertStripToTriangles(vertices).windowed(3).forEach {
                    result.add(convert(it[2], vertexPositions, frame))
                    result.add(convert(it[1], vertexPositions, frame))
                    result.add(convert(it[0], vertexPositions, frame))
                }
                (-numOfPoints * 3)
            }

        }

        return result.indices.map { it.toShort() }.toShortArray() to result.flatMap { listOf(it.x, it.y, it.z, it.s, it.t) }.toFloatArray()
    }

    private fun convertStripToTriangles(vertices: List<VertexInfo>): List<VertexInfo> {
        val result = mutableListOf<VertexInfo>()
        vertices.drop(1).windowed(2).forEach {
            result.add(vertices.first())
            result.add(it.first())
            result.add(it.last())
        }
        return result
    }

    fun readMd2Model(): Md2Model {
        val byteBuffer = ByteBuffer
            .wrap(Gdx.files.internal("tris.md2").readBytes())
            .order(ByteOrder.LITTLE_ENDIAN)
        return Md2Model(byteBuffer, "her")
    }


    override fun render() {
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        batch.use {
            it.draw(image, 140f, 210f)
        }

        camera.update();

        modelBatch.begin(camera)
        models.forEach {
            modelBatch.render(it, environment)
        }
        modelBatch.end()
    }

    override fun dispose() {
        batch.dispose()
        image.dispose()
        models.forEach { it.model.dispose() }
        modelBatch.dispose()
    }
}
