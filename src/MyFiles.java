import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by m on 17.02.16.
 */
public class MyFiles {


    String user_id,  detour_id;
    String path="";
    boolean is_complete;

    String make_name(){
        String filename=path+user_id+detour_id;
        return filename;
    }

    void rename(String from){
        File file_from=new File(from);
        File file_to=new File(from+"complete");
        file_from.renameTo(file_to);
    }

    public void saveAdd(String string){


        try {

                FileWriter fileWriter = new FileWriter(path + make_name(), true);
                fileWriter.append(string+"\n");
                fileWriter.flush();
                fileWriter.close();



        }
        catch (IOException e){

        }
    }



}
