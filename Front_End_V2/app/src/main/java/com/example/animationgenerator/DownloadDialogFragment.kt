package com.example.animationgenerator

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.example.animationgenerator.Ext.getDeviceSize
import com.example.animationgenerator.databinding.FragmentDownloadDialogBinding

class DownloadDialogFragment: DialogFragment() {
    private var binding: FragmentDownloadDialogBinding? = null
    var downloadListener: DownloadListener? = null

    interface DownloadListener {
        fun onClickDownload()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentDownloadDialogBinding.inflate(inflater, container, false).let {
        binding = it
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.setCancelable(false)
        it.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.textviewUpdateButton?.setOnClickListener {
            downloadListener?.onClickDownload()
            dismiss()
        }
        binding?.textviewLaterButton?.setOnClickListener {
            dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
        setDialogSize()
    }

    private fun setDialogSize() {
        val deviceSize = requireActivity().getDeviceSize()
        val deviceWidth = deviceSize[0]
        val params = dialog?.window?.attributes
        params?.width = (deviceWidth*0.848).toInt()
        dialog?.window?.attributes = params as WindowManager.LayoutParams
    }

    fun setDownloadListener(callback: DownloadListener): DownloadDialogFragment {
        this.downloadListener = callback
        return this
    }
}