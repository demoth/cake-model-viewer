package org.demoth.cake

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.UBJsonReader
import ktx.graphics.use


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
            ModelInstance(createModel()),
            loadObjModel(),
            loadG3dModel()
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
