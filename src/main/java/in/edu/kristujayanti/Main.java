package in.edu.kristujayanti;

import in.edu.kristujayanti.handlers.SampleHandler;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        VertxOptions options = new VertxOptions();
        Vertx vertx = Vertx.vertx(options);
        DeploymentOptions deploymentOptions = new DeploymentOptions();

        vertx.deployVerticle(new SampleHandler(), deploymentOptions)
                .onSuccess(id -> System.out.println("Verticle deployed successfully with ID: " + id))
                .onFailure(err -> System.err.println("Deployment failed: " + err.getMessage()));
        //Initialize Vertx Here
        //Establish Mongodb Connection Here
    }
}