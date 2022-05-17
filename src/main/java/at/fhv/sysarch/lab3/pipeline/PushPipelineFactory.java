package at.fhv.sysarch.lab3.pipeline;

import at.fhv.sysarch.lab3.animation.AnimationRenderer;
import at.fhv.sysarch.lab3.obj.Face;
import at.fhv.sysarch.lab3.obj.Model;
import at.fhv.sysarch.lab3.pipeline.data.Pair;
import at.fhv.sysarch.lab3.pipeline.filters.*;
import com.hackoeur.jglm.Mat4;
import com.hackoeur.jglm.Matrices;
import com.hackoeur.jglm.Vec3;
import javafx.animation.AnimationTimer;
import javafx.scene.paint.Color;

public class PushPipelineFactory {
    public static AnimationTimer createPipeline(PipelineData pd) {

        // TODO: the connection of filters and pipes requires a lot of boilerplate code. Think about options how this can be minimized

        // TODO: push from the source (model)
        DataSource<Model> dataSource = new DataSource<>();
        ModelViewTransformationFilter<Face> modelViewFilter = new ModelViewTransformationFilter<>(pd);
        BackfaceCullingFilter<Face> backfaceCullingFilter = new BackfaceCullingFilter<>();
        ColorFilter<Face> colorFilter = new ColorFilter<>(pd);
        LightingFilter lightingFilter = new LightingFilter(pd);
        PerspectiveProjectionFilter perspectiveProjectionFilter = new PerspectiveProjectionFilter(pd);
        ScreenSpaceTransformationFilter screenSpaceTransformationFilter = new ScreenSpaceTransformationFilter(pd);
        DataSink dataSink = new DataSink(pd);

        // TODO 1. perform model-view transformation from model to VIEW SPACE coordinates
        Pipe<Face> toModelViewFilter = new Pipe<>();
        dataSource.setPipeSuccessor(toModelViewFilter);
        toModelViewFilter.setSuccessor(modelViewFilter);

        // TODO 2. perform backface culling in VIEW SPACE
        Pipe<Face> toBackfaceCullingFilter = new Pipe<>();
        modelViewFilter.setPipeSuccessor(toBackfaceCullingFilter);
        toBackfaceCullingFilter.setSuccessor(backfaceCullingFilter);

        // TODO 3. perform depth sorting in VIEW SPACE
        // Not possible (without a hack) in the push pipeline

        // TODO 4. add coloring (space unimportant)
        Pipe<Face> toColorFilter = new Pipe<>();
        backfaceCullingFilter.setPipeSuccessor(toColorFilter);
        toColorFilter.setSuccessor(colorFilter);

        // lighting can be switched on/off
        if (pd.isPerformLighting()) {
            // 4a. perform lighting in VIEW SPACE
            Pipe<Pair<Face, Color>> toLightingFilter = new Pipe<>();
            colorFilter.setPipeSuccessor(toLightingFilter);
            toLightingFilter.setSuccessor(lightingFilter);

            // 5. perform projection transformation on VIEW SPACE coordinates
            Pipe<Pair<Face, Color>> lightingtoPerspectivePipe = new Pipe<>();
            lightingFilter.setPipeSuccessor(lightingtoPerspectivePipe);
            lightingtoPerspectivePipe.setSuccessor(perspectiveProjectionFilter);
        } else {
            // 5. perform projection transformation
            Pipe<Pair<Face, Color>> colorToPerspectivePipe = new Pipe<>();
            colorFilter.setPipeSuccessor(colorToPerspectivePipe);
            colorToPerspectivePipe.setSuccessor(perspectiveProjectionFilter);
        }

        // perform perspective division to screen coordinates
        Pipe<Pair<Face, Color>> toScreenSpaceTransformation = new Pipe<>();
        perspectiveProjectionFilter.setPipeSuccessor(toScreenSpaceTransformation);
        toScreenSpaceTransformation.setSuccessor(screenSpaceTransformationFilter);

        // feed into the sink (renderer)
        Pipe<Pair<Face, Color>> toSink = new Pipe<>();
        screenSpaceTransformationFilter.setPipeSuccessor(toSink);
        toSink.setSuccessor(dataSink);

        // returning an animation renderer which handles clearing of the
        // viewport and computation of the praction
        return new AnimationRenderer(pd) {

            // rotation variable goes in here
            float rotation = 0f;

            /** This method is called for every frame from the JavaFX Animation
             * system (using an AnimationTimer, see AnimationRenderer). 
             * @param fraction the time which has passed since the last render call in a fraction of a second
             * @param model    the model to render 
             */
            @Override
            protected void render(float fraction, Model model) {

                // compute rotation in radians
                rotation += fraction;
                double radiant = rotation % (2 * Math.PI); // 2 PI = 360°

                // create new model rotation matrix using pd.modelRotAxis
                Vec3 modelRotAxis = pd.getModelRotAxis();

                // compute updated model-view tranformation
                Mat4 rotationMatrix = Matrices.rotate((float) radiant, modelRotAxis);

                // update model-view filter
                modelViewFilter.setRotationMatrix(rotationMatrix);

                // trigger rendering of the pipeline
                dataSource.write(model);
            }
        };
    }
}