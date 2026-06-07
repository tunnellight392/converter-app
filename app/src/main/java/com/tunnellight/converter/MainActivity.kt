package com.tunnellight.converter

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tunnellight.converter.model.UnitsRepository

/** Home screen: a tiled grid of every conversion category. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        val recycler = findViewById<RecyclerView>(R.id.categoriesRecycler)
        // Keep the last row clear of the system navigation bar.
        val basePaddingBottom = recycler.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = basePaddingBottom + bars.bottom)
            insets
        }

        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = CategoryAdapter(UnitsRepository.categories) { category ->
            startActivity(
                Intent(this, ConverterActivity::class.java)
                    .putExtra(ConverterActivity.EXTRA_CATEGORY_ID, category.id)
            )
        }
    }
}
