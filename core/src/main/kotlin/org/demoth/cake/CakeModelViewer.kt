package org.demoth.cake

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.UBJsonReader
import ktx.graphics.use
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
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
    }

    /**
     * Create a model programmatically using the model builder
     */
    private fun createModel(): Model {
        val modelBuilder = ModelBuilder()
        val box = modelBuilder.createBox(
            2f, 2f, 2f,
            Material(ColorAttribute.createDiffuse(Color.BLUE)),
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
        val frame = md2Model.frames.first()!!
        val vertexBuffer = frame.points.flatMap { listOf(
            it.x * frame.scale[0],
            it.y * frame.scale[1],
            it.z * frame.scale[2],
            // normals aren't actually used for rendering in q2
//            VERTEXNORMALS[it.normalIndex][0],
//            VERTEXNORMALS[it.normalIndex][1],
//            VERTEXNORMALS[it.normalIndex][2],
        ) }.toFloatArray()
        val vertexIndices: ShortArray = createVertexIndices(md2Model)
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val meshBuilder = modelBuilder.part("part1", GL_TRIANGLES, VertexAttributes(VertexAttribute.Position()), Material(ColorAttribute.createDiffuse(Color.GREEN)))
        meshBuilder.addMesh(vertexBuffer, vertexIndices)
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    private fun createVertexIndices(model: Md2Model): ShortArray {
        var glCmdIndex = 0 // todo: use queue to pop elements instead of using mutable index?
        val result = mutableListOf<Short>()
        while (true) {
            val numOfPoints = model.glCmds[glCmdIndex]
            glCmdIndex++
            glCmdIndex += if (numOfPoints == 0) {
                break
            } else if (numOfPoints >= 0) {
                // triangle strip
                val vertices = mutableListOf<Int>()
                for (i in glCmdIndex until (glCmdIndex + numOfPoints * 3) step 3) {
                    vertices.add(model.glCmds[i + 2])
                }
                // converting strips into separate triangles
                var clockwise = false // when converting a triangle strip into a set of separate triangles, need to alternate the winding direction
                vertices.windowed(3).forEach {
                    if (clockwise) {
                        result.add(it[0].toShort())
                        result.add(it[1].toShort())
                        result.add(it[2].toShort())
                    } else {
                        result.add(it[2].toShort())
                        result.add(it[1].toShort())
                        result.add(it[0].toShort())
                    }
                    clockwise = !clockwise
                }
                numOfPoints * 3
            } else {
                // triangle fan
                val vertices = mutableListOf<Int>()
                for (i in glCmdIndex until (glCmdIndex - numOfPoints * 3) step 3) {
                    vertices.add(model.glCmds[i + 2])
                }
                convertStripToTriangles(vertices).windowed(3).forEach {
                    result.add(it[2].toShort())
                    result.add(it[1].toShort())
                    result.add(it[0].toShort())
                }
                (-numOfPoints * 3)
            }

        }
        return result.toShortArray()
    }

    private fun convertStripToTriangles(vertices: List<Int>): List<Int> {
        val result = mutableListOf<Int>()
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
