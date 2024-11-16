package com.eyesack.freshlist

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerViewAdapter: ShoppingListAdapter
    private val shoppingListItems = mutableListOf<String>()
    private val crossedOffItems = mutableListOf<String>()  // Change to MutableList
    private val sharedPreferencesName = "shopping_list_prefs"
    private val listKey = "shopping_list"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val cameraButton = findViewById<Button>(R.id.cameraButton)

        cameraButton.setOnClickListener {
            // Regular short press: start CameraActivity to open the camera
            startActivity(Intent(this, CameraActivity::class.java).apply {
                putExtra("use_image_picker", false)
            })
        }

        cameraButton.setOnLongClickListener {
            // Long press: start CameraActivity with an extra flag to use the image picker
            startActivity(Intent(this, CameraActivity::class.java).apply {
                putExtra("use_image_picker", true)
            })
            true // Return true to indicate the event was handled
        }
        // Load the saved data from SharedPreferences
        loadData()

        // Set up the RecyclerView with the shopping list
        recyclerViewAdapter = ShoppingListAdapter(shoppingListItems, crossedOffItems)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerViewAdapter

        // Add new item to the shopping list
        val addButton = findViewById<Button>(R.id.addButton)
        val itemInput = findViewById<EditText>(R.id.itemInput)

        addButton.setOnClickListener {
            val newItem = itemInput.text.toString().trim()
            if (newItem.isNotEmpty()) {
                // Check if the item already exists in the list
                val existingIndex = shoppingListItems.indexOf(newItem)

                if (existingIndex != -1) {
                    // Item already exists in the list
                    if (newItem in crossedOffItems) {
                        // If it's crossed off, un-cross it
                        crossedOffItems.remove(newItem)
                    }
                    // Move the item to the top
                    shoppingListItems.removeAt(existingIndex)
                }

                // Add the item to the top of the list
                shoppingListItems.add(0, newItem)
                recyclerViewAdapter.notifyDataSetChanged()  // Update the whole list to reflect the change
                recyclerView.scrollToPosition(0)
                itemInput.text.clear()

                // Save updated list and crossed-off items
                saveData()
            }
        }


        // Swipe to remove items from the shopping list
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = shoppingListItems[position]

                // Remove the item from shoppingListItems
                shoppingListItems.removeAt(position)

                // Also remove the item from crossedOffItems if it's there
                crossedOffItems.remove(item)

                // Notify the adapter and save the updated data
                recyclerViewAdapter.notifyItemRemoved(position)
                saveData()
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // If items to cross off were passed via Intent (from CameraActivity), process them
        handleIncomingCrossedOffItems()
    }

    // This function loads shopping list and crossed-off items from SharedPreferences
    fun saveData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        // Save the shopping list
        val json = gson.toJson(shoppingListItems)
        editor.putString(listKey, json)

        // Save crossed-off items
        val crossedOffJson = gson.toJson(crossedOffItems.distinct()) // Save unique crossed-off items only
        editor.putString("crossed_off_items", crossedOffJson)

        editor.apply()
    }

    private fun loadData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val gson = Gson()

        // Load the shopping list
        val json = sharedPreferences.getString(listKey, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<String>>() {}.type
            shoppingListItems.clear() // Clear any existing data
            shoppingListItems.addAll(gson.fromJson(json, type))
        }

        // Load crossed-off items, ensuring no duplicates
        val crossedOffJson = sharedPreferences.getString("crossed_off_items", null)
        if (crossedOffJson != null) {
            val type = object : TypeToken<List<String>>() {}.type  // Use List<String> to avoid type inference error
            val tempCrossedOffItems = gson.fromJson<List<String>>(crossedOffJson, type) // Temporary list to hold loaded items
            crossedOffItems.clear() // Clear any existing data
            crossedOffItems.addAll(tempCrossedOffItems.distinct()) // Add unique items only
        }
    }




    // This function handles items to be crossed off passed via an Intent (from CameraActivity)
    private fun handleIncomingCrossedOffItems() {
        val itemsToCrossOff = intent.getStringArrayListExtra("items_to_cross_off")
        if (itemsToCrossOff != null) {
            itemsToCrossOff.forEach { item ->
                if (item !in crossedOffItems) { // Avoid duplicates
                    crossedOffItems.add(item)
                    val index = shoppingListItems.indexOf(item)
                    if (index != -1) {
                        shoppingListItems.removeAt(index)
                        shoppingListItems.add(item) // Move item to the bottom
                    }
                }
            }
            recyclerViewAdapter.notifyDataSetChanged()
            saveData() // Save the updated crossed-off items
        }
    }

    // This function will be called when CameraActivity sends an Intent with crossed-off items
    override fun onResume() {
        super.onResume()
        handleIncomingCrossedOffItems()
    }
}
