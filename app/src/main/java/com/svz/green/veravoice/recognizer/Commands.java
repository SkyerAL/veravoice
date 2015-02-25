package com.svz.green.veravoice.recognizer;

import android.content.Context;
import android.graphics.Color;

import com.svz.green.veravoice.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Green on 22.01.2015.
 */
public class Commands {

    private List<Command> commands;

    public Commands(Context activity) {
        commands = new ArrayList<>();

//        commands.add(new Command(activity.getString(R.string.command_blue), Color.BLUE));
//        commands.add(new Command(activity.getString(R.string.command_red), Color.RED));
//        commands.add(new Command(activity.getString(R.string.command_green), Color.GREEN));
//        commands.add(new Command(activity.getString(R.string.command_yellow), Color.YELLOW));

        commands.add(new Command(activity.getString(R.string.command_activate_1)));
        commands.add(new Command(activity.getString(R.string.command_activate_2)));
        //commands.add(new Command(activity.getString(R.string.command_activate_3)));

        commands.add(new Command(activity.getString(R.string.command_start_1)));
        commands.add(new Command(activity.getString(R.string.command_start_2)));

        commands.add(new Command(activity.getString(R.string.command_repeat_1)));
        commands.add(new Command(activity.getString(R.string.command_repeat_2)));
        commands.add(new Command(activity.getString(R.string.command_repeat_3)));

        commands.add(new Command(activity.getString(R.string.command_confirm_1)));
        commands.add(new Command(activity.getString(R.string.command_confirm_2)));
        commands.add(new Command(activity.getString(R.string.command_confirm_3)));

        commands.add(new Command(activity.getString(R.string.command_stop_1)));
        commands.add(new Command(activity.getString(R.string.command_stop_2)));

    }

    public List<Command> getCommands() {
        return commands;
    }

    public int findColorByText(String text) {
        if (text == null || text.isEmpty()) {
            return Color.BLACK;
        }

        for (Command command : commands) {
            if (command.getText().equals(text)) {
                return command.getColor();
            }
        }

        return Color.BLACK;
    }
}
