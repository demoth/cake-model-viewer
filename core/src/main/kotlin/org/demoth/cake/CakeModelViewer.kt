package org.demoth.cake

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_LINE_STRIP
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
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
            loadMd2Model() // fixme: nothing is rendered.. :(
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
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
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
        val vertexBuffer = frame.points.flatMap { listOf(it.x * frame.scale[0], it.y * frame.scale[1], it.z * frame.scale[2]) }.toFloatArray()

        val vertexIndices: ShortArray = createVertexIndices(md2Model, frame)

        val meshBuilder = MeshBuilder()
        meshBuilder.begin(VertexAttributes(VertexAttribute.Position()), GL_TRIANGLES)
        meshBuilder.addMesh(vertexBuffer, vertexIndices)
        val mesh = meshBuilder.end()
        // create a model from the created mesh
        val model = Model()
        model.materials.add(Material(ColorAttribute.createEmissive(Color.GREEN)))
        model.meshes.add(mesh)
        val modelInstance = ModelInstance(model)
        modelInstance.transform.translate(2f, 0f, 0f)
        modelInstance.transform.scale(0.05f, 0.05f, 0.05f)
        return modelInstance
    }

    private fun createVertexIndices(model: Md2Model, frame: Md2Frame): ShortArray {
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
                vertices.windowed(3).forEach {
                    result.add(it[0].toShort())
                    result.add(it[1].toShort())
                    result.add(it[2].toShort())
                }
                numOfPoints * 3
            } else {
                // triangle fan
                val vertices = mutableListOf<Int>()
                for (i in glCmdIndex until (glCmdIndex - numOfPoints * 3) step 3) {
                    vertices.add(model.glCmds[i + 2])
                }
                convertStripToTriangles(vertices).windowed(3).forEach {
                    result.add(it[0].toShort())
                    result.add(it[1].toShort())
                    result.add(it[2].toShort())
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
            .wrap(Gdx.files.internal("adrenaline-model.md2").readBytes())
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
