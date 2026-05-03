package com.aigy.securenote.service;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QuickRecordTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        
        // 100% 强制收起面板的最佳实践：
        // 启动一个独立的跳板 Activity，该 Activity 在独立任务栈运行，
        // 既能触发 startActivityAndCollapse 强制收起面板，
        // 又不会因为启动 Activity 而把已经在后台的 App 主界面拉到前台。
        Intent intent = new Intent(this, QuickRecordTrampolineActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        startActivityAndCollapse(intent);

        updateTileState(Tile.STATE_ACTIVE);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState(Tile.STATE_INACTIVE);
    }

    private void updateTileState(int state) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(state);
            tile.updateTile();
        }
    }
}
